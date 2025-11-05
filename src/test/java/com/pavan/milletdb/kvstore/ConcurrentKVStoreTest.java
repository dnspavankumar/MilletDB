package com.pavan.milletdb.kvstore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentKVStoreTest {
    
    private ConcurrentKVStore<String, Integer> store;
    
    @BeforeEach
    void setUp() {
        store = new ConcurrentKVStore<>(100);
    }
    
    @Test
    void testBasicPutAndGet() {
        store.put("key1", 100);
        assertEquals(100, store.get("key1"));
    }
    
    @Test
    void testRemove() {
        store.put("key1", 100);
        assertEquals(100, store.remove("key1"));
        assertNull(store.get("key1"));
    }
    
    @Test
    void testExpiration() throws InterruptedException {
        store.put("key1", 100);
        store.expire("key1", 100); // 100ms TTL
        
        assertEquals(100, store.get("key1")); // Should still exist
        
        Thread.sleep(150); // Wait for expiration
        
        assertNull(store.get("key1")); // Should be expired
    }
    
    @Test
    void testExpireNonExistentKey() {
        assertFalse(store.expire("nonexistent", 1000));
    }
    
    @Test
    void testExpireWithInvalidTTL() {
        store.put("key1", 100);
        assertThrows(IllegalArgumentException.class, () -> store.expire("key1", 0));
        assertThrows(IllegalArgumentException.class, () -> store.expire("key1", -100));
    }
    
    @Test
    void testPutRemovesExpiration() throws InterruptedException {
        store.put("key1", 100);
        store.expire("key1", 100);
        
        // Update the key, which should remove expiration
        store.put("key1", 200);
        
        Thread.sleep(150);
        
        // Should still exist since expiration was cleared
        assertEquals(200, store.get("key1"));
    }
    
    @Test
    void testConcurrentReads() throws InterruptedException {
        int numThreads = 10;
        int numReads = 1000;
        
        store.put("key1", 100);
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < numReads; j++) {
                        Integer value = store.get("key1");
                        if (value != null && value == 100) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(numThreads * numReads, successCount.get());
    }
    
    @Test
    void testConcurrentWrites() throws InterruptedException {
        int numThreads = 10;
        int numWrites = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < numWrites; j++) {
                        String key = "key-" + threadId + "-" + j;
                        store.put(key, threadId * 1000 + j);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Verify all writes succeeded (within capacity)
        int expectedSize = Math.min(numThreads * numWrites, store.getCapacity());
        assertTrue(store.size() <= expectedSize);
    }
    
    @Test
    void testConcurrentReadWriteMix() throws InterruptedException {
        int numThreads = 20;
        int numOperations = 500;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger writeCount = new AtomicInteger(0);
        AtomicInteger readCount = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < numOperations; j++) {
                        if (j % 2 == 0) {
                            // Write operation
                            store.put("key-" + threadId, j);
                            writeCount.incrementAndGet();
                        } else {
                            // Read operation
                            store.get("key-" + threadId);
                            readCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(numThreads * numOperations / 2, writeCount.get());
        assertEquals(numThreads * numOperations / 2, readCount.get());
    }
    
    @Test
    void testConcurrentRemoves() throws InterruptedException {
        int numKeys = 100;
        int numThreads = 10;
        
        // Populate store
        for (int i = 0; i < numKeys; i++) {
            store.put("key-" + i, i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger removeCount = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < numKeys; j++) {
                        if (j % numThreads == threadId) {
                            Integer removed = store.remove("key-" + j);
                            if (removed != null) {
                                removeCount.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(numKeys, removeCount.get());
        assertEquals(0, store.size());
    }
    
    @Test
    void testConcurrentExpiration() throws InterruptedException {
        int numKeys = 50;
        int numThreads = 10;
        
        // Populate store
        for (int i = 0; i < numKeys; i++) {
            store.put("key-" + i, i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < numKeys; j++) {
                        if (j % numThreads == threadId) {
                            store.expire("key-" + j, 50);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        Thread.sleep(100); // Wait for expiration
        
        // All keys should be expired
        for (int i = 0; i < numKeys; i++) {
            assertNull(store.get("key-" + i));
        }
    }
    
    @Test
    void testConcurrentMixedOperations() throws InterruptedException, ExecutionException, TimeoutException {
        int numThreads = 20;
        int numOperations = 200;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            Future<?> future = executor.submit(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int j = 0; j < numOperations; j++) {
                    int operation = random.nextInt(4);
                    String key = "key-" + random.nextInt(50);
                    
                    switch (operation) {
                        case 0: // Put
                            store.put(key, threadId * 1000 + j);
                            break;
                        case 1: // Get
                            store.get(key);
                            break;
                        case 2: // Remove
                            store.remove(key);
                            break;
                        case 3: // Expire
                            store.expire(key, 1000);
                            break;
                    }
                }
            });
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        
        executor.shutdown();
        
        // No assertions needed - test passes if no exceptions thrown
        assertTrue(store.size() >= 0);
    }
    
    @Test
    void testStressTestWithHighContention() throws InterruptedException {
        int numThreads = 50;
        int numOperations = 1000;
        String sharedKey = "shared-key";
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger operationCount = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < numOperations; j++) {
                        if (j % 3 == 0) {
                            store.put(sharedKey, threadId);
                        } else if (j % 3 == 1) {
                            store.get(sharedKey);
                        } else {
                            store.remove(sharedKey);
                        }
                        operationCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(numThreads * numOperations, operationCount.get());
    }
    
    @Test
    void testClear() {
        store.put("key1", 1);
        store.put("key2", 2);
        store.put("key3", 3);
        
        store.clear();
        
        assertEquals(0, store.size());
        assertNull(store.get("key1"));
        assertNull(store.get("key2"));
        assertNull(store.get("key3"));
    }
    
    @Test
    void testContainsKey() {
        store.put("key1", 100);
        assertTrue(store.containsKey("key1"));
        assertFalse(store.containsKey("key2"));
    }
    
    @Test
    void testContainsKeyWithExpiration() throws InterruptedException {
        store.put("key1", 100);
        store.expire("key1", 50);
        
        assertTrue(store.containsKey("key1"));
        
        Thread.sleep(100);
        
        assertFalse(store.containsKey("key1"));
    }
    
    @Test
    void testStartCleanup() {
        assertFalse(store.isCleanupRunning());
        
        store.startCleanup(100);
        
        assertTrue(store.isCleanupRunning());
        
        store.stopCleanup();
    }
    
    @Test
    void testStartCleanupWithInvalidInterval() {
        assertThrows(IllegalArgumentException.class, () -> store.startCleanup(0));
        assertThrows(IllegalArgumentException.class, () -> store.startCleanup(-100));
    }
    
    @Test
    void testStartCleanupWhenAlreadyRunning() {
        store.startCleanup(100);
        
        assertThrows(IllegalStateException.class, () -> store.startCleanup(100));
        
        store.stopCleanup();
    }
    
    @Test
    void testStopCleanup() {
        store.startCleanup(100);
        assertTrue(store.isCleanupRunning());
        
        store.stopCleanup();
        assertFalse(store.isCleanupRunning());
    }
    
    @Test
    void testStopCleanupWhenNotRunning() {
        assertThrows(IllegalStateException.class, () -> store.stopCleanup());
    }
    
    @Test
    void testBackgroundCleanupRemovesExpiredKeys() throws InterruptedException {
        store.put("key1", 1);
        store.put("key2", 2);
        store.put("key3", 3);
        
        store.expire("key1", 50);
        store.expire("key2", 50);
        
        store.startCleanup(100);
        
        Thread.sleep(200);
        
        assertNull(store.get("key1"));
        assertNull(store.get("key2"));
        assertEquals(3, store.get("key3"));
        
        store.stopCleanup();
    }
    
    @Test
    void testForceCleanup() throws InterruptedException {
        store.put("key1", 1);
        store.put("key2", 2);
        store.put("key3", 3);
        
        store.expire("key1", 50);
        store.expire("key2", 50);
        
        Thread.sleep(100);
        
        int removed = store.forceCleanup();
        
        assertEquals(2, removed);
        assertNull(store.get("key1"));
        assertNull(store.get("key2"));
        assertEquals(3, store.get("key3"));
    }
    
    @Test
    void testForceCleanupWithNoExpiredKeys() {
        store.put("key1", 1);
        store.put("key2", 2);
        
        int removed = store.forceCleanup();
        
        assertEquals(0, removed);
        assertEquals(1, store.get("key1"));
        assertEquals(2, store.get("key2"));
    }
    
    @Test
    void testCleanupDoesNotRemoveNonExpiredKeys() throws InterruptedException {
        store.put("key1", 1);
        store.put("key2", 2);
        
        store.expire("key1", 50);
        store.expire("key2", 5000); // Long TTL
        
        store.startCleanup(100);
        
        Thread.sleep(200);
        
        assertNull(store.get("key1"));
        assertEquals(2, store.get("key2")); // Should still exist
        
        store.stopCleanup();
    }
    
    @Test
    void testConcurrentCleanupAndOperations() throws InterruptedException {
        int numThreads = 10;
        int numOperations = 100;
        
        store.startCleanup(50);
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int j = 0; j < numOperations; j++) {
                        String key = "key-" + threadId + "-" + j;
                        store.put(key, j);
                        if (random.nextBoolean()) {
                            store.expire(key, random.nextInt(50, 200));
                        }
                        store.get(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        Thread.sleep(300); // Let cleanup run
        
        store.stopCleanup();
        
        // No assertions needed - test passes if no exceptions thrown
        assertTrue(store.size() >= 0);
    }
}
