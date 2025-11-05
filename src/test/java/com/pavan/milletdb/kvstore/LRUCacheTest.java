package com.pavan.milletdb.kvstore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LRUCacheTest {
    
    private LRUCache<String, Integer> cache;
    
    @BeforeEach
    void setUp() {
        cache = new LRUCache<>(3);
    }
    
    @Test
    void testConstructorWithInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<String, Integer>(0));
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<String, Integer>(-1));
    }
    
    @Test
    void testPutAndGet() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        
        assertEquals(1, cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(3, cache.get("c"));
        assertEquals(3, cache.size());
    }
    
    @Test
    void testGetNonExistentKey() {
        cache.put("a", 1);
        assertNull(cache.get("b"));
    }
    
    @Test
    void testUpdateExistingKey() {
        cache.put("a", 1);
        cache.put("a", 10);
        
        assertEquals(10, cache.get("a"));
        assertEquals(1, cache.size());
    }
    
    @Test
    void testEvictionWhenFull() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        
        // Cache is full, adding "d" should evict "a" (least recently used)
        cache.put("d", 4);
        
        assertNull(cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
        assertEquals(3, cache.size());
    }
    
    @Test
    void testAccessReordering() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        
        // Access "a" to make it most recently used
        cache.get("a");
        
        // Now "b" is least recently used, should be evicted
        cache.put("d", 4);
        
        assertEquals(1, cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
    }
    
    @Test
    void testPutReordering() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        
        // Update "a" to make it most recently used
        cache.put("a", 10);
        
        // Now "b" is least recently used, should be evicted
        cache.put("d", 4);
        
        assertEquals(10, cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
    }
    
    @Test
    void testRemove() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        
        assertEquals(2, cache.remove("b"));
        assertEquals(2, cache.size());
        assertNull(cache.get("b"));
        
        // After removal, can add new entry without eviction
        cache.put("d", 4);
        assertEquals(3, cache.size());
        assertEquals(1, cache.get("a"));
        assertEquals(3, cache.get("c"));
        assertEquals(4, cache.get("d"));
    }
    
    @Test
    void testRemoveNonExistentKey() {
        cache.put("a", 1);
        assertNull(cache.remove("b"));
        assertEquals(1, cache.size());
    }
    
    @Test
    void testClear() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        
        cache.clear();
        
        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertNull(cache.get("c"));
    }
    
    @Test
    void testCapacity() {
        assertEquals(3, cache.getCapacity());
        
        LRUCache<String, Integer> smallCache = new LRUCache<>(1);
        assertEquals(1, smallCache.getCapacity());
    }
    
    @Test
    void testSingleCapacityCache() {
        LRUCache<String, Integer> smallCache = new LRUCache<>(1);
        
        smallCache.put("a", 1);
        assertEquals(1, smallCache.get("a"));
        
        smallCache.put("b", 2);
        assertNull(smallCache.get("a"));
        assertEquals(2, smallCache.get("b"));
    }
    
    @Test
    void testComplexEvictionScenario() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        
        // Access order: a, b, c (c is most recent)
        cache.get("a");  // Order: b, c, a
        cache.get("b");  // Order: c, a, b
        
        cache.put("d", 4);  // Should evict c
        
        assertNull(cache.get("c"));
        assertEquals(1, cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(4, cache.get("d"));
    }
    
    @Test
    void testNullKeyAndValue() {
        cache.put(null, 1);
        assertEquals(1, cache.get(null));
        
        cache.put("a", null);
        assertNull(cache.get("a"));
        assertEquals(2, cache.size());
    }
}
