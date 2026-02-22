package com.pavan.milletdb.snapshot;

import com.pavan.milletdb.kvstore.ConcurrentKVStore;
import com.pavan.milletdb.kvstore.ShardedKVStore;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages periodic snapshots of ShardedKVStore to disk in compact binary form.
 * Supports automatic periodic snapshots and manual snapshot/restore operations.
 */
public class SnapshotManager {
    
    private final Path snapshotDirectory;
    private ScheduledExecutorService snapshotExecutor;
    private ScheduledFuture<?> snapshotTask;
    private volatile boolean snapshotRunning;
    
    private static final String SNAPSHOT_FILE_PREFIX = "snapshot-";
    private static final String SNAPSHOT_FILE_EXTENSION = ".bin";
    private static final long DEFAULT_SNAPSHOT_INTERVAL_SECONDS = 30;
    
    /**
     * Creates a SnapshotManager with the specified snapshot directory.
     *
     * @param snapshotDirectory directory where snapshots will be stored
     * @throws IOException if directory cannot be created
     */
    public SnapshotManager(String snapshotDirectory) throws IOException {
        this.snapshotDirectory = Paths.get(snapshotDirectory);
        Files.createDirectories(this.snapshotDirectory);
        this.snapshotRunning = false;
    }
    
    /**
     * Starts automatic periodic snapshots every 30 seconds.
     *
     * @param store the store to snapshot
     * @throws IllegalStateException if snapshots are already running
     */
    public synchronized <K, V> void startPeriodicSnapshots(ShardedKVStore<K, V> store) {
        startPeriodicSnapshots(store, DEFAULT_SNAPSHOT_INTERVAL_SECONDS);
    }
    
    /**
     * Starts automatic periodic snapshots with custom interval.
     *
     * @param store the store to snapshot
     * @param intervalSeconds interval between snapshots in seconds
     * @throws IllegalStateException if snapshots are already running
     */
    public synchronized <K, V> void startPeriodicSnapshots(ShardedKVStore<K, V> store, long intervalSeconds) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("Snapshot interval must be positive");
        }
        if (snapshotRunning) {
            throw new IllegalStateException("Periodic snapshots are already running");
        }
        
        snapshotExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Snapshot-Manager");
            thread.setDaemon(true);
            return thread;
        });
        
        snapshotTask = snapshotExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    saveSnapshot(store);
                } catch (Exception e) {
                    System.err.println("Error during periodic snapshot: " + e.getMessage());
                }
            },
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
        
        snapshotRunning = true;
    }
    
    /**
     * Stops automatic periodic snapshots.
     *
     * @throws IllegalStateException if snapshots are not running
     */
    public synchronized void stopPeriodicSnapshots() {
        if (!snapshotRunning) {
            throw new IllegalStateException("Periodic snapshots are not running");
        }
        
        if (snapshotTask != null) {
            snapshotTask.cancel(false);
            snapshotTask = null;
        }
        
        if (snapshotExecutor != null) {
            snapshotExecutor.shutdown();
            try {
                if (!snapshotExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    snapshotExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                snapshotExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            snapshotExecutor = null;
        }
        
        snapshotRunning = false;
    }
    
    /**
     * Checks if periodic snapshots are running.
     *
     * @return true if snapshots are running
     */
    public boolean isSnapshotRunning() {
        return snapshotRunning;
    }
    
    /**
     * Manually saves a snapshot of the store to disk.
     *
     * @param store the store to snapshot
     * @return the path to the saved snapshot file
     * @throws IOException if snapshot cannot be saved
     */
    public <K, V> Path saveSnapshot(ShardedKVStore<K, V> store) throws IOException {
        long timestamp = System.currentTimeMillis();
        String filename = SNAPSHOT_FILE_PREFIX + timestamp + SNAPSHOT_FILE_EXTENSION;
        Path snapshotFile = snapshotDirectory.resolve(filename);
        Path tempFile = snapshotDirectory.resolve(filename + ".tmp");

        SnapshotData<K, V> snapshotData = captureSnapshot(store);
        try (ObjectOutputStream out = new ObjectOutputStream(
            new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
            out.writeObject(snapshotData);
        }

        try {
            Files.move(tempFile, snapshotFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveFailure) {
            Files.move(tempFile, snapshotFile, StandardCopyOption.REPLACE_EXISTING);
        }

        return snapshotFile;
    }
    
    /**
     * Loads the most recent snapshot from disk into the store.
     *
     * @param store the store to restore into
     * @return true if a snapshot was loaded, false if no snapshot exists
     * @throws IOException if snapshot cannot be loaded
     */
    public <K, V> boolean loadLatestSnapshot(ShardedKVStore<K, V> store) throws IOException {
        File latestSnapshot = findLatestSnapshot();
        if (latestSnapshot == null) {
            return false;
        }
        
        return loadSnapshot(store, latestSnapshot.toPath());
    }
    
    /**
     * Loads a specific snapshot file into the store.
     *
     * @param store the store to restore into
     * @param snapshotFile the snapshot file to load
     * @return true if snapshot was loaded successfully
     * @throws IOException if snapshot cannot be loaded
     */
    @SuppressWarnings("unchecked")
    public <K, V> boolean loadSnapshot(ShardedKVStore<K, V> store, Path snapshotFile) throws IOException {
        if (!Files.exists(snapshotFile)) {
            return false;
        }

        SnapshotData<K, V> snapshotData;
        try (ObjectInputStream in = new ObjectInputStream(
            new BufferedInputStream(Files.newInputStream(snapshotFile)))) {
            snapshotData = (SnapshotData<K, V>) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize snapshot: " + snapshotFile, e);
        }
        
        restoreSnapshot(store, snapshotData);
        return true;
    }
    
    /**
     * Captures the current state of the store.
     */
    private <K, V> SnapshotData<K, V> captureSnapshot(ShardedKVStore<K, V> store) {
        SnapshotData<K, V> data = new SnapshotData<>();
        data.timestamp = System.currentTimeMillis();
        data.numShards = store.getNumShards();
        data.capacityPerShard = store.getCapacityPerShard();
        data.shards = store.captureSnapshot();
        
        return data;
    }
    
    /**
     * Restores snapshot data into the store.
     */
    private <K, V> void restoreSnapshot(ShardedKVStore<K, V> store, SnapshotData<K, V> data) {
        if (data.numShards != store.getNumShards()) {
            throw new IllegalArgumentException(
                "Snapshot shard count (" + data.numShards + 
                ") does not match store shard count (" + store.getNumShards() + ")"
            );
        }
        
        store.restoreSnapshot(data.shards);
    }
    
    /**
     * Finds the most recent snapshot file.
     */
    private File findLatestSnapshot() {
        File[] files = snapshotDirectory.toFile().listFiles(
            (dir, name) -> name.startsWith(SNAPSHOT_FILE_PREFIX) && 
                          name.endsWith(SNAPSHOT_FILE_EXTENSION)
        );
        
        if (files == null || files.length == 0) {
            return null;
        }
        
        File latest = files[0];
        for (File file : files) {
            if (file.lastModified() > latest.lastModified()) {
                latest = file;
            }
        }
        
        return latest;
    }
    
    /**
     * Deletes old snapshots, keeping only the specified number of most recent ones.
     *
     * @param keepCount number of snapshots to keep
     * @return number of snapshots deleted
     */
    public int cleanupOldSnapshots(int keepCount) {
        if (keepCount < 0) {
            throw new IllegalArgumentException("Keep count must be non-negative");
        }
        
        File[] files = snapshotDirectory.toFile().listFiles(
            (dir, name) -> name.startsWith(SNAPSHOT_FILE_PREFIX) && 
                          name.endsWith(SNAPSHOT_FILE_EXTENSION)
        );
        
        if (files == null || files.length <= keepCount) {
            return 0;
        }
        
        // Sort by last modified time (newest first)
        java.util.Arrays.sort(files, (a, b) -> 
            Long.compare(b.lastModified(), a.lastModified())
        );
        
        int deleted = 0;
        for (int i = keepCount; i < files.length; i++) {
            if (files[i].delete()) {
                deleted++;
            }
        }
        
        return deleted;
    }
    
    /**
     * Data structure for binary snapshot serialization.
     */
    static class SnapshotData<K, V> implements Serializable {
        private static final long serialVersionUID = 1L;
        public long timestamp;
        public int numShards;
        public int capacityPerShard;
        public Map<Integer, Map<K, ConcurrentKVStore.SnapshotEntry<V>>> shards;
        
        public SnapshotData() {
            this.shards = new HashMap<>();
        }
    }
}
