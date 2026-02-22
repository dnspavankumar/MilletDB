package com.pavan.milletdb.kvstore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Sharded key-value store that distributes keys across multiple shards for improved concurrency.
 * Uses hash-based sharding to minimize lock contention.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class ShardedKVStore<K, V> {
    
    private final ConcurrentKVStore<K, V>[] shards;
    private final int shardMask;
    private final int maxKeyBytes;
    private final int maxValueBytes;
    private final ReentrantReadWriteLock snapshotGate;
    
    /**
     * Creates a sharded KV store with the specified number of shards and capacity per shard.
     *
     * @param numShards number of shards (must be a power of 2)
     * @param capacityPerShard capacity for each individual shard
     */
    @SuppressWarnings("unchecked")
    public ShardedKVStore(int numShards, int capacityPerShard) {
        this(numShards, capacityPerShard, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Creates a sharded KV store with explicit per-entry size limits.
     *
     * @param numShards number of shards (must be a power of 2)
     * @param capacityPerShard capacity for each individual shard
     * @param maxKeyBytes maximum allowed key size in UTF-8 bytes (for String keys)
     * @param maxValueBytes maximum allowed value size in UTF-8 bytes (for String values)
     */
    @SuppressWarnings("unchecked")
    public ShardedKVStore(int numShards, int capacityPerShard, int maxKeyBytes, int maxValueBytes) {
        if (numShards <= 0 || (numShards & (numShards - 1)) != 0) {
            throw new IllegalArgumentException("Number of shards must be a positive power of 2");
        }
        if (capacityPerShard <= 0) {
            throw new IllegalArgumentException("Capacity per shard must be positive");
        }
        if (maxKeyBytes <= 0 || maxValueBytes <= 0) {
            throw new IllegalArgumentException("Max key/value bytes must be positive");
        }
        
        this.shards = (ConcurrentKVStore<K, V>[]) new ConcurrentKVStore[numShards];
        this.shardMask = numShards - 1;
        this.maxKeyBytes = maxKeyBytes;
        this.maxValueBytes = maxValueBytes;
        this.snapshotGate = new ReentrantReadWriteLock();
        
        for (int i = 0; i < numShards; i++) {
            shards[i] = new ConcurrentKVStore<>(capacityPerShard);
        }
    }

    private static int utf8Size(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof String str) {
            return str.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        if (obj instanceof byte[] bytes) {
            return bytes.length;
        }
        return -1;
    }

    private void validateEntrySize(K key, V value) {
        int keySize = utf8Size(key);
        if (keySize >= 0 && keySize > maxKeyBytes) {
            throw new IllegalArgumentException("Key size exceeds limit: " + keySize + " > " + maxKeyBytes + " bytes");
        }

        int valueSize = utf8Size(value);
        if (valueSize >= 0 && valueSize > maxValueBytes) {
            throw new IllegalArgumentException("Value size exceeds limit: " + valueSize + " > " + maxValueBytes + " bytes");
        }
    }
    
    /**
     * Determines the shard index for a given key using hash-based sharding.
     *
     * @param key the key to hash
     * @return the shard index
     */
    private int getShardIndex(K key) {
        if (key == null) {
            return 0;
        }
        // Use hash code with mask for efficient modulo operation
        int hash = key.hashCode();
        // Spread bits to reduce collisions
        hash ^= (hash >>> 16);
        return hash & shardMask;
    }
    
    /**
     * Gets the shard responsible for a given key.
     *
     * @param key the key
     * @return the shard that handles this key
     */
    private ConcurrentKVStore<K, V> getShard(K key) {
        return shards[getShardIndex(key)];
    }
    
    /**
     * Inserts or updates a key-value pair in the appropriate shard.
     *
     * @param key the key to insert or update
     * @param value the value to associate with the key
     */
    public void put(K key, V value) {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            validateEntrySize(key, value);
            getShard(key).put(key, value);
        } finally {
            gate.unlock();
        }
    }
    
    /**
     * Retrieves a value by key from the appropriate shard.
     *
     * @param key the key to look up
     * @return the value associated with the key, or null if not found or expired
     */
    public V get(K key) {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            return getShard(key).get(key);
        } finally {
            gate.unlock();
        }
    }
    
    /**
     * Removes a key-value pair from the appropriate shard.
     *
     * @param key the key to remove
     * @return the value that was removed, or null if key not found
     */
    public V remove(K key) {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            return getShard(key).remove(key);
        } finally {
            gate.unlock();
        }
    }
    
    /**
     * Sets a TTL (time-to-live) for a key in the appropriate shard.
     *
     * @param key the key to set expiration for
     * @param ttlMillis time-to-live in milliseconds
     * @return true if the key exists and expiration was set, false otherwise
     */
    public boolean expire(K key, long ttlMillis) {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            return getShard(key).expire(key, ttlMillis);
        } finally {
            gate.unlock();
        }
    }
    
    /**
     * Returns the total size across all shards.
     */
    public int size() {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            int totalSize = 0;
            for (ConcurrentKVStore<K, V> shard : shards) {
                totalSize += shard.size();
            }
            return totalSize;
        } finally {
            gate.unlock();
        }
    }
    
    /**
     * Returns the number of shards.
     */
    public int getNumShards() {
        return shards.length;
    }
    
    /**
     * Returns the capacity per shard.
     */
    public int getCapacityPerShard() {
        return shards[0].getCapacity();
    }
    
    /**
     * Returns the total capacity across all shards.
     */
    public int getTotalCapacity() {
        return shards.length * getCapacityPerShard();
    }
    
    /**
     * Clears all entries from all shards.
     */
    public void clear() {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            for (ConcurrentKVStore<K, V> shard : shards) {
                shard.clear();
            }
        } finally {
            gate.unlock();
        }
    }
    
    /**
     * Checks if a key exists in the appropriate shard and has not expired.
     *
     * @param key the key to check
     * @return true if the key exists and has not expired
     */
    public boolean containsKey(K key) {
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            return getShard(key).containsKey(key);
        } finally {
            gate.unlock();
        }
    }
    
    /**
     * Returns the size of a specific shard (for monitoring/debugging).
     *
     * @param shardIndex the shard index
     * @return the size of the specified shard
     */
    public int getShardSize(int shardIndex) {
        if (shardIndex < 0 || shardIndex >= shards.length) {
            throw new IllegalArgumentException("Invalid shard index");
        }
        ReentrantReadWriteLock.ReadLock gate = snapshotGate.readLock();
        gate.lock();
        try {
            return shards[shardIndex].size();
        } finally {
            gate.unlock();
        }
    }

    /**
     * Captures a globally consistent snapshot view across all shards.
     *
     * @return shard-indexed snapshot entries
     */
    public Map<Integer, Map<K, ConcurrentKVStore.SnapshotEntry<V>>> captureSnapshot() {
        ReentrantReadWriteLock.WriteLock gate = snapshotGate.writeLock();
        gate.lock();
        try {
            Map<Integer, Map<K, ConcurrentKVStore.SnapshotEntry<V>>> snapshot = new HashMap<>();
            for (int i = 0; i < shards.length; i++) {
                snapshot.put(i, shards[i].getAllEntries());
            }
            return snapshot;
        } finally {
            gate.unlock();
        }
    }

    /**
     * Restores shard snapshots while blocking concurrent store operations.
     *
     * @param snapshot shard-indexed snapshot entries
     */
    public void restoreSnapshot(Map<Integer, Map<K, ConcurrentKVStore.SnapshotEntry<V>>> snapshot) {
        ReentrantReadWriteLock.WriteLock gate = snapshotGate.writeLock();
        gate.lock();
        try {
            for (int i = 0; i < shards.length; i++) {
                Map<K, ConcurrentKVStore.SnapshotEntry<V>> shardData = snapshot.get(i);
                if (shardData != null) {
                    shards[i].restoreFromSnapshot(shardData);
                }
            }
        } finally {
            gate.unlock();
        }
    }
    
    /**
     * Returns all shards (for snapshot operations).
     * Public for use by SnapshotManager.
     *
     * @return array of all shards
     */
    public ConcurrentKVStore<K, V>[] getShards() {
        return shards;
    }
}
