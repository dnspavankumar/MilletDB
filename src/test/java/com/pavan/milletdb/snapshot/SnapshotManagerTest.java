package com.pavan.milletdb.snapshot;

import com.pavan.milletdb.kvstore.ShardedKVStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotManagerTest {
    
    @TempDir
    Path tempDir;
    
    private SnapshotManager snapshotManager;
    private ShardedKVStore<String, Integer> store;
    
    @BeforeEach
    void setUp() throws IOException {
        snapshotManager = new SnapshotManager(tempDir.toString());
        store = new ShardedKVStore<>(4, 100);
    }
    
    @AfterEach
    void tearDown() {
        if (snapshotManager.isSnapshotRunning()) {
            snapshotManager.stopPeriodicSnapshots();
        }
    }
    
    @Test
    void testSaveAndLoadSnapshot() throws IOException {
        // Populate store
        store.put("key1", 100);
        store.put("key2", 200);
        store.put("key3", 300);
        
        // Save snapshot
        Path snapshotFile = snapshotManager.saveSnapshot(store);
        assertTrue(Files.exists(snapshotFile));
        
        // Create new store and load snapshot
        ShardedKVStore<String, Integer> newStore = new ShardedKVStore<>(4, 100);
        boolean loaded = snapshotManager.loadSnapshot(newStore, snapshotFile);
        
        assertTrue(loaded);
        assertEquals(100, newStore.get("key1"));
        assertEquals(200, newStore.get("key2"));
        assertEquals(300, newStore.get("key3"));
    }
    
    @Test
    void testLoadLatestSnapshot() throws IOException, InterruptedException {
        // Save multiple snapshots
        store.put("key1", 100);
        snapshotManager.saveSnapshot(store);
        
        Thread.sleep(10);
        
        store.put("key2", 200);
        snapshotManager.saveSnapshot(store);
        
        // Load latest into new store
        ShardedKVStore<String, Integer> newStore = new ShardedKVStore<>(4, 100);
        boolean loaded = snapshotManager.loadLatestSnapshot(newStore);
        
        assertTrue(loaded);
        assertEquals(100, newStore.get("key1"));
        assertEquals(200, newStore.get("key2"));
    }
    
    @Test
    void testLoadNonExistentSnapshot() throws IOException {
        ShardedKVStore<String, Integer> newStore = new ShardedKVStore<>(4, 100);
        boolean loaded = snapshotManager.loadLatestSnapshot(newStore);
        
        assertFalse(loaded);
    }
    
    @Test
    void testSnapshotWithExpiration() throws IOException, InterruptedException {
        store.put("key1", 100);
        store.put("key2", 200);
        store.expire("key2", 100);
        
        // Save snapshot
        Path snapshotFile = snapshotManager.saveSnapshot(store);
        
        // Wait for expiration
        Thread.sleep(150);
        
        // Load into new store
        ShardedKVStore<String, Integer> newStore = new ShardedKVStore<>(4, 100);
        snapshotManager.loadSnapshot(newStore, snapshotFile);
        
        // key1 should exist, key2 should be expired
        assertEquals(100, newStore.get("key1"));
        assertNull(newStore.get("key2"));
    }
    
    @Test
    void testStartPeriodicSnapshots() throws InterruptedException, IOException {
        assertFalse(snapshotManager.isSnapshotRunning());
        
        store.put("key1", 100);
        snapshotManager.startPeriodicSnapshots(store, 1);
        
        assertTrue(snapshotManager.isSnapshotRunning());
        
        // Wait for at least one snapshot
        Thread.sleep(1500);
        
        // Check that snapshot file was created
        long snapshotCount = Files.list(tempDir)
            .filter(p -> p.getFileName().toString().startsWith("snapshot-"))
            .count();
        
        assertTrue(snapshotCount > 0);
        
        snapshotManager.stopPeriodicSnapshots();
        assertFalse(snapshotManager.isSnapshotRunning());
    }
    
    @Test
    void testStartPeriodicSnapshotsWithInvalidInterval() {
        assertThrows(IllegalArgumentException.class, 
            () -> snapshotManager.startPeriodicSnapshots(store, 0));
        assertThrows(IllegalArgumentException.class, 
            () -> snapshotManager.startPeriodicSnapshots(store, -1));
    }
    
    @Test
    void testStartPeriodicSnapshotsWhenAlreadyRunning() {
        snapshotManager.startPeriodicSnapshots(store, 1);
        
        assertThrows(IllegalStateException.class, 
            () -> snapshotManager.startPeriodicSnapshots(store, 1));
        
        snapshotManager.stopPeriodicSnapshots();
    }
    
    @Test
    void testStopPeriodicSnapshotsWhenNotRunning() {
        assertThrows(IllegalStateException.class, 
            () -> snapshotManager.stopPeriodicSnapshots());
    }
    
    @Test
    void testCleanupOldSnapshots() throws IOException, InterruptedException {
        // Create multiple snapshots
        for (int i = 0; i < 5; i++) {
            store.put("key" + i, i);
            snapshotManager.saveSnapshot(store);
            Thread.sleep(10);
        }
        
        // Keep only 2 most recent
        int deleted = snapshotManager.cleanupOldSnapshots(2);
        
        assertEquals(3, deleted);
        
        long remainingCount = Files.list(tempDir)
            .filter(p -> p.getFileName().toString().startsWith("snapshot-"))
            .count();
        
        assertEquals(2, remainingCount);
    }
    
    @Test
    void testCleanupWithNoSnapshots() {
        int deleted = snapshotManager.cleanupOldSnapshots(5);
        assertEquals(0, deleted);
    }
    
    @Test
    void testCleanupWithInvalidKeepCount() {
        assertThrows(IllegalArgumentException.class, 
            () -> snapshotManager.cleanupOldSnapshots(-1));
    }
    
    @Test
    void testSnapshotPreservesMultipleShards() throws IOException {
        // Add data that will be distributed across shards
        for (int i = 0; i < 50; i++) {
            store.put("key-" + i, i);
        }
        
        Path snapshotFile = snapshotManager.saveSnapshot(store);
        
        // Load into new store
        ShardedKVStore<String, Integer> newStore = new ShardedKVStore<>(4, 100);
        snapshotManager.loadSnapshot(newStore, snapshotFile);
        
        // Verify all data
        for (int i = 0; i < 50; i++) {
            assertEquals(i, newStore.get("key-" + i));
        }
    }
    
    @Test
    void testSnapshotWithMismatchedShardCount() throws IOException {
        store.put("key1", 100);
        Path snapshotFile = snapshotManager.saveSnapshot(store);
        
        // Try to load into store with different shard count
        ShardedKVStore<String, Integer> newStore = new ShardedKVStore<>(8, 100);
        
        assertThrows(IllegalArgumentException.class, 
            () -> snapshotManager.loadSnapshot(newStore, snapshotFile));
    }
    
    @Test
    void testEmptyStoreSnapshot() throws IOException {
        Path snapshotFile = snapshotManager.saveSnapshot(store);
        assertTrue(Files.exists(snapshotFile));
        
        ShardedKVStore<String, Integer> newStore = new ShardedKVStore<>(4, 100);
        boolean loaded = snapshotManager.loadSnapshot(newStore, snapshotFile);
        
        assertTrue(loaded);
        assertEquals(0, newStore.size());
    }
}
