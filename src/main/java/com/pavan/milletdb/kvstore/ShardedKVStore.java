package com.pavan.milletdb.kvstore;

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
    
    /**
     * Creates a sharded KV store with the specified number of shards and capacity per shard.
     *
     * @param numShards number of shards (must be a power of 2)
     * @param capacityPerShard capacity for each individual shard
     */
    @SuppressWarnings("unchecked")
    public ShardedKVStore(int numShards, int capacityPerShard) {
        if (numShards <= 0 || (numShards & (numShards - 1)) != 0) {
            throw new IllegalArgumentException("Number of shards must be a positive power of 2");
        }
        if (capacityPerShard <= 0) {
            throw new IllegalArgumentException("Capacity per shard must be positive");
        }
        
        this.shards = (ConcurrentKVStore<K, V>[]) new ConcurrentKVStore[numShards];
        this.shardMask = numShards - 1;
        
        for (int i = 0; i < numShards; i++) {
            shards[i] = new ConcurrentKVStore<>(capacityPerShard);
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
        getShard(key).put(key, value);
    }
    
    /**
     * Retrieves a value by key from the appropriate shard.
     *
     * @param key the key to look up
     * @return the value associated with the key, or null if not found or expired
     */
    public V get(K key) {
        return getShard(key).get(key);
    }
    
    /**
     * Removes a key-value pair from the appropriate shard.
     *
     * @param key the key to remove
     * @return the value that was removed, or null if key not found
     */
    public V remove(K key) {
        return getShard(key).remove(key);
    }
    
    /**
     * Sets a TTL (time-to-live) for a key in the appropriate shard.
     *
     * @param key the key to set expiration for
     * @param ttlMillis time-to-live in milliseconds
     * @return true if the key exists and expiration was set, false otherwise
     */
    public boolean expire(K key, long ttlMillis) {
        return getShard(key).expire(key, ttlMillis);
    }
    
    /**
     * Returns the total size across all shards.
     */
    public int size() {
        int totalSize = 0;
        for (ConcurrentKVStore<K, V> shard : shards) {
            totalSize += shard.size();
        }
        return totalSize;
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
        for (ConcurrentKVStore<K, V> shard : shards) {
            shard.clear();
        }
    }
    
    /**
     * Checks if a key exists in the appropriate shard and has not expired.
     *
     * @param key the key to check
     * @return true if the key exists and has not expired
     */
    public boolean containsKey(K key) {
        return getShard(key).containsKey(key);
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
        return shards[shardIndex].size();
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
