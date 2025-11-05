package com.pavan.milletdb.kvstore;

/**
 * Doubly linked node for key-value storage.
 * Key and value are immutable, but links can be modified.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
public class Node<K, V> {
    
    private final K key;
    private final V value;
    Node<K, V> prev;
    Node<K, V> next;
    
    public Node(K key, V value) {
        this.key = key;
        this.value = value;
    }
    
    public K getKey() {
        return key;
    }
    
    public V getValue() {
        return value;
    }
}
