package com.pavan.milletdb.kvstore;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.pavan.milletdb.metrics.StatsCollector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe key-value store with LRU eviction and TTL support.
 * Uses ReentrantReadWriteLock for concurrent access control.
 * Supports background cleanup of expired entries and statistics collection.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class ConcurrentKVStore<K, V> {
    
    private static final int DEFAULT_STRIPE_COUNT = 64;

    private final Cache<K, V> cache;
    private final Map<K, Long> expirationMap;
    private final ReentrantLock[] stripedLocks;
    private final int capacity;
    private final StatsCollector stats;
    private ScheduledExecutorService cleanupExecutor;
    private ScheduledFuture<?> cleanupTask;
    private volatile boolean cleanupRunning;
    
    public ConcurrentKVStore(int capacity) {
        this(capacity, new StatsCollector());
    }
    
    public ConcurrentKVStore(int capacity, StatsCollector stats) {
        this.capacity = capacity;
        this.expirationMap = new ConcurrentHashMap<>();
        this.cache = Caffeine.newBuilder()
            .maximumSize(capacity)
            .removalListener((K key, V value, RemovalCause cause) -> {
                if (cause == RemovalCause.SIZE) {
                    stats.recordEviction();
                }
                if (key != null && cause != RemovalCause.REPLACED && cause != RemovalCause.EXPLICIT) {
                    expirationMap.remove(key);
                }
            })
            .build();
        this.stripedLocks = createStripes(DEFAULT_STRIPE_COUNT);
        this.stats = stats;
        this.cleanupRunning = false;
    }

    private ReentrantLock[] createStripes(int stripeCount) {
        ReentrantLock[] stripes = new ReentrantLock[stripeCount];
        for (int i = 0; i < stripeCount; i++) {
            stripes[i] = new ReentrantLock();
        }
        return stripes;
    }

    private ReentrantLock lockFor(K key) {
        int hash = (key == null) ? 0 : key.hashCode();
        hash ^= (hash >>> 16);
        return stripedLocks[hash & (stripedLocks.length - 1)];
    }
    
    /**
     * Inserts or updates a key-value pair.
     *
     * @param key the key to insert or update
     * @param value the value to associate with the key
     */
    public void put(K key, V value) {
        ReentrantLock keyLock = lockFor(key);
        keyLock.lock();
        try {
            cache.put(key, value);
            expirationMap.remove(key); // Clear any existing expiration
            stats.recordSet();
        } finally {
            keyLock.unlock();
        }
    }
    
    /**
     * Retrieves a value by key. Returns null if key doesn't exist or has expired.
     * Expired entries are lazily removed during access.
     *
     * @param key the key to look up
     * @return the value associated with the key, or null if not found or expired
     */
    public V get(K key) {
        Long expirationTime = expirationMap.get(key);
        long now = System.currentTimeMillis();

        if (expirationTime != null && now > expirationTime) {
            ReentrantLock keyLock = lockFor(key);
            keyLock.lock();
            try {
                Long currentExpiration = expirationMap.get(key);
                if (currentExpiration != null && now > currentExpiration) {
                    cache.invalidate(key);
                    expirationMap.remove(key);
                    stats.recordExpiration();
                    stats.recordGet(false);
                    return null;
                }
            } finally {
                keyLock.unlock();
            }
        }

        V value = cache.getIfPresent(key);
        stats.recordGet(value != null);
        return value;
    }

    private V removeInternal(K key, boolean recordDelete) {
        ReentrantLock keyLock = lockFor(key);
        keyLock.lock();
        try {
            expirationMap.remove(key);
            V removed = cache.getIfPresent(key);
            if (removed != null) {
                cache.invalidate(key);
            }
            if (recordDelete) {
                stats.recordDelete(removed != null);
            }
            return removed;
        } finally {
            keyLock.unlock();
        }
    }
    
    /**
     * Removes a key-value pair from the store.
     *
     * @param key the key to remove
     * @return the value that was removed, or null if key not found
     */
    public V remove(K key) {
        return removeInternal(key, true);
    }
    
    /**
     * Sets a TTL (time-to-live) for a key. The entry will expire after the specified duration.
     *
     * @param key the key to set expiration for
     * @param ttlMillis time-to-live in milliseconds
     * @return true if the key exists and expiration was set, false otherwise
     */
    public boolean expire(K key, long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        
        ReentrantLock keyLock = lockFor(key);
        keyLock.lock();
        try {
            // Check if key exists in cache
            V value = cache.getIfPresent(key);
            boolean existed = value != null;
            
            if (existed) {
                long expirationTime = System.currentTimeMillis() + ttlMillis;
                expirationMap.put(key, expirationTime);
            }
            
            stats.recordExpire(existed);
            return existed;
        } finally {
            keyLock.unlock();
        }
    }
    
    /**
     * Returns the current size of the store.
     */
    public int size() {
        cache.cleanUp();
        return Math.toIntExact(cache.estimatedSize());
    }
    
    /**
     * Returns the capacity of the store.
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Clears all entries from the store.
     */
    public void clear() {
        for (ReentrantLock stripedLock : stripedLocks) {
            stripedLock.lock();
        }
        try {
            cache.invalidateAll();
            expirationMap.clear();
        } finally {
            for (int i = stripedLocks.length - 1; i >= 0; i--) {
                stripedLocks[i].unlock();
            }
        }
    }
    
    /**
     * Checks if a key exists and has not expired.
     *
     * @param key the key to check
     * @return true if the key exists and has not expired
     */
    public boolean containsKey(K key) {
        return get(key) != null;
    }
    
    /**
     * Starts the background cleanup thread that periodically removes expired keys.
     *
     * @param intervalMillis interval between cleanup runs in milliseconds
     * @throws IllegalStateException if cleanup is already running
     */
    public synchronized void startCleanup(long intervalMillis) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("Cleanup interval must be positive");
        }
        if (cleanupRunning) {
            throw new IllegalStateException("Cleanup is already running");
        }
        
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "KVStore-Cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        cleanupTask = cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredKeys,
            intervalMillis,
            intervalMillis,
            TimeUnit.MILLISECONDS
        );
        
        cleanupRunning = true;
    }
    
    /**
     * Stops the background cleanup thread.
     *
     * @throws IllegalStateException if cleanup is not running
     */
    public synchronized void stopCleanup() {
        if (!cleanupRunning) {
            throw new IllegalStateException("Cleanup is not running");
        }
        
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
            cleanupTask = null;
        }
        
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            cleanupExecutor = null;
        }
        
        cleanupRunning = false;
    }
    
    /**
     * Checks if the background cleanup thread is running.
     *
     * @return true if cleanup is running
     */
    public boolean isCleanupRunning() {
        return cleanupRunning;
    }
    
    /**
     * Performs a single cleanup pass, removing all expired keys.
     * This method is called periodically by the background cleanup thread.
     *
     * @return the number of keys removed
     */
    private int cleanupExpiredKeys() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        for (Map.Entry<K, Long> entry : expirationMap.entrySet()) {
            if (currentTime <= entry.getValue()) {
                continue;
            }

            K key = entry.getKey();
            ReentrantLock keyLock = lockFor(key);
            keyLock.lock();
            try {
                Long expirationTime = expirationMap.get(key);
                if (expirationTime != null && currentTime > expirationTime) {
                    V existing = cache.getIfPresent(key);
                    if (existing != null) {
                        cache.invalidate(key);
                    }
                    expirationMap.remove(key);
                    stats.recordExpiration();
                    removedCount++;
                }
            } finally {
                keyLock.unlock();
            }
        }
        
        return removedCount;
    }
    
    /**
     * Manually triggers a cleanup of expired keys.
     * Useful for testing or forcing cleanup outside the scheduled interval.
     *
     * @return the number of keys removed
     */
    public int forceCleanup() {
        return cleanupExpiredKeys();
    }
    
    /**
     * Returns the statistics collector for this store.
     */
    public StatsCollector getStats() {
        return stats;
    }
    
    /**
     * Returns a snapshot of all entries with their expiration times.
     * Public for use by SnapshotManager.
     *
     * @return map of key-value pairs with optional expiration times
     */
    public Map<K, SnapshotEntry<V>> getAllEntries() {
        Map<K, SnapshotEntry<V>> snapshot = new HashMap<>();
        
        // Snapshot over cache keyset; each key is read under its stripe lock.
        for (K key : new ArrayList<>(cache.asMap().keySet())) {
            ReentrantLock keyLock = lockFor(key);
            keyLock.lock();
            try {
                V value = cache.getIfPresent(key);
                if (value == null) {
                    continue;
                }

                Long expiration = expirationMap.get(key);
                if (expiration != null && System.currentTimeMillis() > expiration) {
                    cache.invalidate(key);
                    expirationMap.remove(key);
                    continue;
                }
                snapshot.put(key, new SnapshotEntry<>(value, expiration));
            } finally {
                keyLock.unlock();
            }
        }
        
        return snapshot;
    }
    
    /**
     * Restores entries from a snapshot.
     * Public for use by SnapshotManager.
     *
     * @param entries the entries to restore
     */
    public void restoreFromSnapshot(Map<K, SnapshotEntry<V>> entries) {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<K, SnapshotEntry<V>> entry : entries.entrySet()) {
            K key = entry.getKey();
            SnapshotEntry<V> snapshotEntry = entry.getValue();
            if (snapshotEntry.expirationTime != null && currentTime >= snapshotEntry.expirationTime) {
                continue;
            }

            ReentrantLock keyLock = lockFor(key);
            keyLock.lock();
            try {
                cache.put(key, snapshotEntry.value);
                if (snapshotEntry.expirationTime != null) {
                    expirationMap.put(key, snapshotEntry.expirationTime);
                } else {
                    expirationMap.remove(key);
                }
            } finally {
                keyLock.unlock();
            }
        }
    }
    
    /**
     * Entry for snapshot serialization.
     */
    public static class SnapshotEntry<V> implements Serializable {
        private static final long serialVersionUID = 1L;
        public V value;
        public Long expirationTime;
        
        public SnapshotEntry() {}
        
        public SnapshotEntry(V value, Long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }
    }
}
