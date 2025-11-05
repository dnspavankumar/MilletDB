package com.pavan.milletdb.kvstore;

import java.util.HashMap;
import java.util.Map;

/**
 * Fixed-capacity LRU (Least Recently Used) cache implementation.
 * All operations (put, get, remove) are O(1).
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class LRUCache<K, V> {
    
    private final int capacity;
    private final Map<K, Node<K, V>> cache;
    private final Node<K, V> head; // Most recently used
    private final Node<K, V> tail; // Least recently used
    
    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.cache = new HashMap<>(capacity);
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }
    
    /**
     * Retrieves a value by key. Moves the accessed node to the front (most recently used).
     *
     * @param key the key to look up
     * @return the value associated with the key, or null if not found
     */
    public V get(K key) {
        Node<K, V> node = cache.get(key);
        if (node == null) {
            return null;
        }
        moveToFront(node);
        return node.getValue();
    }
    
    /**
     * Inserts or updates a key-value pair. Evicts the least recently used entry if at capacity.
     *
     * @param key the key to insert or update
     * @param value the value to associate with the key
     */
    public void put(K key, V value) {
        Node<K, V> node = cache.get(key);
        
        if (node != null) {
            // Update existing node and move to front
            removeNode(node);
            Node<K, V> newNode = new Node<>(key, value);
            cache.put(key, newNode);
            addToFront(newNode);
        } else {
            // Insert new node
            if (cache.size() >= capacity) {
                evictLRU();
            }
            Node<K, V> newNode = new Node<>(key, value);
            cache.put(key, newNode);
            addToFront(newNode);
        }
    }
    
    /**
     * Removes a key-value pair from the cache.
     *
     * @param key the key to remove
     * @return the value that was removed, or null if key not found
     */
    public V remove(K key) {
        Node<K, V> node = cache.remove(key);
        if (node == null) {
            return null;
        }
        removeNode(node);
        return node.getValue();
    }
    
    /**
     * Returns the current size of the cache.
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * Returns the capacity of the cache.
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        cache.clear();
        head.next = tail;
        tail.prev = head;
    }
    
    // Helper: Add node right after head (most recently used position)
    private void addToFront(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }
    
    // Helper: Remove node from its current position in the list
    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    // Helper: Move existing node to front (most recently used)
    private void moveToFront(Node<K, V> node) {
        removeNode(node);
        addToFront(node);
    }
    
    // Helper: Evict the least recently used entry (node before tail)
    private void evictLRU() {
        Node<K, V> lru = tail.prev;
        if (lru != head) {
            cache.remove(lru.getKey());
            removeNode(lru);
        }
    }
}
