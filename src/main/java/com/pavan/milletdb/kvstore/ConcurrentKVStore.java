package com.pavan.milletdb.kvstore;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.pavan.milletdb.metrics.StatsCollector;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final Cache<K, ValueRecord<V>> cache;
    private final int capacity;
    private final StatsCollector stats;
    private final ReentrantReadWriteLock snapshotGate;

    private ScheduledExecutorService cleanupExecutor;
    private ScheduledFuture<?> cleanupTask;
    private volatile boolean cleanupRunning;

    public ConcurrentKVStore(int capacity) {
        this(capacity, new StatsCollector());
    }

    public ConcurrentKVStore(int capacity, StatsCollector stats) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.stats = stats;
        this.snapshotGate = new ReentrantReadWriteLock();
        this.cache = Caffeine.newBuilder()
            .maximumSize(capacity)
            .removalListener((K key, ValueRecord<V> value, RemovalCause cause) -> {
                if (cause == RemovalCause.SIZE) {
                    stats.recordEviction();
                }
            })
            .build();
        this.cleanupRunning = false;
    }

    private boolean isExpired(ValueRecord<V> record, long nowMillis) {
        return record.expirationTime != null && nowMillis >= record.expirationTime;
    }

    /**
     * Inserts or updates a key-value pair.
     *
     * @param key the key to insert or update
     * @param value the value to associate with the key
     */
    public void put(K key, V value) {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            // Value + TTL metadata live in one record, so GET does not wait on PUT.
            cache.put(key, new ValueRecord<>(value, null));
            stats.recordSet();
        } finally {
            gate.unlock();
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
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            ValueRecord<V> record = cache.getIfPresent(key);
            if (record == null) {
                stats.recordGet(false);
                return null;
            }

            long now = System.currentTimeMillis();
            if (isExpired(record, now)) {
                if (cache.asMap().remove(key, record)) {
                    stats.recordExpiration();
                }
                stats.recordGet(false);
                return null;
            }

            stats.recordGet(true);
            return record.value;
        } finally {
            gate.unlock();
        }
    }

    private V removeInternal(K key, boolean recordDelete) {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            ValueRecord<V> removed = cache.asMap().remove(key);
            if (recordDelete) {
                stats.recordDelete(removed != null);
            }
            return removed == null ? null : removed.value;
        } finally {
            gate.unlock();
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

        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            long now = System.currentTimeMillis();
            long expirationTime = now + ttlMillis;
            AtomicBoolean existed = new AtomicBoolean(false);
            AtomicBoolean removedExpired = new AtomicBoolean(false);

            cache.asMap().compute(key, (k, current) -> {
                if (current == null) {
                    return null;
                }
                if (isExpired(current, now)) {
                    removedExpired.set(true);
                    return null;
                }
                existed.set(true);
                return current.withExpiration(expirationTime);
            });

            if (removedExpired.get()) {
                stats.recordExpiration();
            }
            boolean keyExists = existed.get();
            stats.recordExpire(keyExists);
            return keyExists;
        } finally {
            gate.unlock();
        }
    }

    /**
     * Returns the current size of the store.
     */
    public int size() {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            cache.cleanUp();
            return Math.toIntExact(cache.estimatedSize());
        } finally {
            gate.unlock();
        }
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
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            cache.invalidateAll();
        } finally {
            gate.unlock();
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
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            long now = System.currentTimeMillis();
            int removedCount = 0;

            for (Map.Entry<K, ValueRecord<V>> entry : cache.asMap().entrySet()) {
                ValueRecord<V> record = entry.getValue();
                if (!isExpired(record, now)) {
                    continue;
                }
                if (cache.asMap().remove(entry.getKey(), record)) {
                    stats.recordExpiration();
                    removedCount++;
                }
            }

            return removedCount;
        } finally {
            gate.unlock();
        }
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
        ReentrantReadWriteLock.WriteLock gate = snapshotGate.writeLock();
        gate.lock();
        try {
            long now = System.currentTimeMillis();
            Map<K, SnapshotEntry<V>> snapshot = new HashMap<>();

            for (Map.Entry<K, ValueRecord<V>> entry : cache.asMap().entrySet()) {
                ValueRecord<V> record = entry.getValue();
                if (isExpired(record, now)) {
                    if (cache.asMap().remove(entry.getKey(), record)) {
                        stats.recordExpiration();
                    }
                    continue;
                }
                snapshot.put(entry.getKey(), new SnapshotEntry<>(record.value, record.expirationTime));
            }

            return snapshot;
        } finally {
            gate.unlock();
        }
    }

    /**
     * Restores entries from a snapshot.
     * Public for use by SnapshotManager.
     *
     * @param entries the entries to restore
     */
    public void restoreFromSnapshot(Map<K, SnapshotEntry<V>> entries) {
        ReentrantReadWriteLock.WriteLock gate = snapshotGate.writeLock();
        gate.lock();
        try {
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<K, SnapshotEntry<V>> entry : entries.entrySet()) {
                K key = entry.getKey();
                SnapshotEntry<V> snapshotEntry = entry.getValue();
                if (snapshotEntry.expirationTime != null && currentTime >= snapshotEntry.expirationTime) {
                    continue;
                }

                cache.put(key, new ValueRecord<>(snapshotEntry.value, snapshotEntry.expirationTime));
            }
        } finally {
            gate.unlock();
        }
    }

    private static class ValueRecord<V> {
        private final V value;
        private final Long expirationTime;

        private ValueRecord(V value, Long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }

        private ValueRecord<V> withExpiration(Long expirationTime) {
            return new ValueRecord<>(value, expirationTime);
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
