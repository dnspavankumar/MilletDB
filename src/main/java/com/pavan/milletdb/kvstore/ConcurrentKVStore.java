package com.pavan.milletdb.kvstore;

import com.pavan.milletdb.metrics.StatsCollector;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe key-value store with LRU eviction and TTL support.
 * Uses ReentrantReadWriteLock for concurrent access control.
 * Supports background cleanup of expired entries and statistics collection.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class ConcurrentKVStore<K, V> {
    
    private final LRUCache<K, V> cache;
    private final Map<K, Long> expirationMap;
    private final Set<K> keys; // Track all keys for snapshot
    private final ReadWriteLock lock;
    private final StatsCollector stats;
    private ScheduledExecutorService cleanupExecutor;
    private ScheduledFuture<?> cleanupTask;
    private volatile boolean cleanupRunning;
    
    public ConcurrentKVStore(int capacity) {
        this(capacity, new StatsCollector());
    }
    
    public ConcurrentKVStore(int capacity, StatsCollector stats) {
        this.cache = new LRUCache<>(capacity);
        this.expirationMap = new ConcurrentHashMap<>();
        this.keys = ConcurrentHashMap.newKeySet();
        this.lock = new ReentrantReadWriteLock();
        this.stats = stats;
        this.cleanupRunning = false;
    }
    
    /**
     * Inserts or updates a key-value pair.
     *
     * @param key the key to insert or update
     * @param value the value to associate with the key
     */
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            cache.put(key, value);
            keys.add(key);
            expirationMap.remove(key); // Clear any existing expiration
            stats.recordSet();
        } finally {
            lock.writeLock().unlock();
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
        lock.readLock().lock();
        try {
            // Check expiration first
            Long expirationTime = expirationMap.get(key);
            if (expirationTime != null && System.currentTimeMillis() > expirationTime) {
                // Upgrade to write lock for removal
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    // Double-check after acquiring write lock
                    expirationTime = expirationMap.get(key);
                    if (expirationTime != null && System.currentTimeMillis() > expirationTime) {
                        cache.remove(key);
                        keys.remove(key);
                        expirationMap.remove(key);
                        stats.recordExpiration();
                        stats.recordGet(false);
                        return null;
                    }
                    // If not expired anymore, downgrade to read lock
                    lock.readLock().lock();
                } finally {
                    lock.writeLock().unlock();
                }
            }
            
            V value = cache.get(key);
            stats.recordGet(value != null);
            return value;
        } finally {
            try {
                lock.readLock().unlock();
            } catch (IllegalMonitorStateException e) {
                // Already unlocked during lock upgrade
            }
        }
    }
    
    /**
     * Removes a key-value pair from the store.
     *
     * @param key the key to remove
     * @return the value that was removed, or null if key not found
     */
    public V remove(K key) {
        lock.writeLock().lock();
        try {
            keys.remove(key);
            expirationMap.remove(key);
            V removed = cache.remove(key);
            stats.recordDelete(removed != null);
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
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
        
        lock.writeLock().lock();
        try {
            // Check if key exists in cache
            V value = cache.get(key);
            boolean existed = value != null;
            
            if (existed) {
                long expirationTime = System.currentTimeMillis() + ttlMillis;
                expirationMap.put(key, expirationTime);
            }
            
            stats.recordExpire(existed);
            return existed;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Returns the current size of the store.
     */
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Returns the capacity of the store.
     */
    public int getCapacity() {
        return cache.getCapacity();
    }
    
    /**
     * Clears all entries from the store.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            keys.clear();
            expirationMap.clear();
        } finally {
            lock.writeLock().unlock();
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
        List<K> expiredKeys = new ArrayList<>();
        
        // First pass: identify expired keys (read lock)
        lock.readLock().lock();
        try {
            for (Map.Entry<K, Long> entry : expirationMap.entrySet()) {
                if (currentTime > entry.getValue()) {
                    expiredKeys.add(entry.getKey());
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // Second pass: remove expired keys (write lock)
        if (!expiredKeys.isEmpty()) {
            lock.writeLock().lock();
            try {
                for (K key : expiredKeys) {
                    // Double-check expiration under write lock
                    Long expirationTime = expirationMap.get(key);
                    if (expirationTime != null && currentTime > expirationTime) {
                        cache.remove(key);
                        keys.remove(key);
                        expirationMap.remove(key);
                        stats.recordExpiration();
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        return expiredKeys.size();
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
        
        lock.readLock().lock();
        try {
            for (K key : keys) {
                V value = cache.get(key);
                if (value != null) {
                    Long expiration = expirationMap.get(key);
                    snapshot.put(key, new SnapshotEntry<>(value, expiration));
                }
            }
        } finally {
            lock.readLock().unlock();
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
        lock.writeLock().lock();
        try {
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<K, SnapshotEntry<V>> entry : entries.entrySet()) {
                K key = entry.getKey();
                SnapshotEntry<V> snapshotEntry = entry.getValue();
                
                // Only restore non-expired entries
                if (snapshotEntry.expirationTime == null || 
                    currentTime < snapshotEntry.expirationTime) {
                    cache.put(key, snapshotEntry.value);
                    keys.add(key);
                    if (snapshotEntry.expirationTime != null) {
                        expirationMap.put(key, snapshotEntry.expirationTime);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Entry for snapshot serialization.
     */
    public static class SnapshotEntry<V> {
        public V value;
        public Long expirationTime;
        
        public SnapshotEntry() {}
        
        public SnapshotEntry(V value, Long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }
    }
}
