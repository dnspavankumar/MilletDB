package com.pavan.milletdb.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and aggregates statistics for the key-value store.
 * Thread-safe using atomic counters.
 */
public class StatsCollector {
    
    // Operation counters
    private final AtomicLong totalGets;
    private final AtomicLong totalSets;
    private final AtomicLong totalDeletes;
    private final AtomicLong totalExpires;
    
    // Hit/miss counters
    private final AtomicLong cacheHits;
    private final AtomicLong cacheMisses;
    
    // Eviction counters
    private final AtomicLong evictions;
    
    // Expiration counters
    private final AtomicLong expirations;
    
    // Timing
    private final long startTime;
    
    public StatsCollector() {
        this.totalGets = new AtomicLong(0);
        this.totalSets = new AtomicLong(0);
        this.totalDeletes = new AtomicLong(0);
        this.totalExpires = new AtomicLong(0);
        this.cacheHits = new AtomicLong(0);
        this.cacheMisses = new AtomicLong(0);
        this.evictions = new AtomicLong(0);
        this.expirations = new AtomicLong(0);
        this.startTime = System.currentTimeMillis();
    }
    
    // Operation recording methods
    
    public void recordGet(boolean hit) {
        totalGets.incrementAndGet();
        if (hit) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
    }
    
    public void recordSet() {
        totalSets.incrementAndGet();
    }
    
    public void recordDelete(boolean existed) {
        totalDeletes.incrementAndGet();
        if (existed) {
            // Successfully deleted
        }
    }
    
    public void recordExpire(boolean existed) {
        totalExpires.incrementAndGet();
    }
    
    public void recordEviction() {
        evictions.incrementAndGet();
    }
    
    public void recordExpiration() {
        expirations.incrementAndGet();
    }
    
    // Getter methods
    
    public long getTotalGets() {
        return totalGets.get();
    }
    
    public long getTotalSets() {
        return totalSets.get();
    }
    
    public long getTotalDeletes() {
        return totalDeletes.get();
    }
    
    public long getTotalExpires() {
        return totalExpires.get();
    }
    
    public long getCacheHits() {
        return cacheHits.get();
    }
    
    public long getCacheMisses() {
        return cacheMisses.get();
    }
    
    public long getEvictions() {
        return evictions.get();
    }
    
    public long getExpirations() {
        return expirations.get();
    }
    
    public long getTotalOperations() {
        return totalGets.get() + totalSets.get() + totalDeletes.get() + totalExpires.get();
    }
    
    public double getHitRate() {
        long hits = cacheHits.get();
        long total = hits + cacheMisses.get();
        return total == 0 ? 0.0 : (double) hits / total;
    }
    
    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTime;
    }
    
    public long getUptimeSeconds() {
        return getUptimeMillis() / 1000;
    }
    
    /**
     * Returns a snapshot of current statistics.
     */
    public StatsSnapshot getSnapshot() {
        return new StatsSnapshot(
            totalGets.get(),
            totalSets.get(),
            totalDeletes.get(),
            totalExpires.get(),
            cacheHits.get(),
            cacheMisses.get(),
            evictions.get(),
            expirations.get(),
            getUptimeMillis()
        );
    }
    
    /**
     * Resets all statistics counters.
     */
    public void reset() {
        totalGets.set(0);
        totalSets.set(0);
        totalDeletes.set(0);
        totalExpires.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        evictions.set(0);
        expirations.set(0);
    }
    
    /**
     * Merges statistics from another collector into this one.
     * Useful for aggregating shard statistics.
     */
    public void merge(StatsCollector other) {
        totalGets.addAndGet(other.totalGets.get());
        totalSets.addAndGet(other.totalSets.get());
        totalDeletes.addAndGet(other.totalDeletes.get());
        totalExpires.addAndGet(other.totalExpires.get());
        cacheHits.addAndGet(other.cacheHits.get());
        cacheMisses.addAndGet(other.cacheMisses.get());
        evictions.addAndGet(other.evictions.get());
        expirations.addAndGet(other.expirations.get());
    }
    
    @Override
    public String toString() {
        return String.format(
            "StatsCollector{gets=%d, sets=%d, deletes=%d, expires=%d, " +
            "hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d, expirations=%d, uptime=%ds}",
            totalGets.get(), totalSets.get(), totalDeletes.get(), totalExpires.get(),
            cacheHits.get(), cacheMisses.get(), getHitRate() * 100,
            evictions.get(), expirations.get(), getUptimeSeconds()
        );
    }
    
    /**
     * Immutable snapshot of statistics at a point in time.
     */
    public static class StatsSnapshot {
        public final long totalGets;
        public final long totalSets;
        public final long totalDeletes;
        public final long totalExpires;
        public final long cacheHits;
        public final long cacheMisses;
        public final long evictions;
        public final long expirations;
        public final long uptimeMillis;
        
        public StatsSnapshot(long totalGets, long totalSets, long totalDeletes, long totalExpires,
                           long cacheHits, long cacheMisses, long evictions, long expirations,
                           long uptimeMillis) {
            this.totalGets = totalGets;
            this.totalSets = totalSets;
            this.totalDeletes = totalDeletes;
            this.totalExpires = totalExpires;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.evictions = evictions;
            this.expirations = expirations;
            this.uptimeMillis = uptimeMillis;
        }
        
        public long getTotalOperations() {
            return totalGets + totalSets + totalDeletes + totalExpires;
        }
        
        public double getHitRate() {
            long total = cacheHits + cacheMisses;
            return total == 0 ? 0.0 : (double) cacheHits / total;
        }
        
        @Override
        public String toString() {
            return String.format(
                "StatsSnapshot{operations=%d, hits=%d, misses=%d, hitRate=%.2f%%, " +
                "evictions=%d, expirations=%d, uptime=%dms}",
                getTotalOperations(), cacheHits, cacheMisses, getHitRate() * 100,
                evictions, expirations, uptimeMillis
            );
        }
    }
}
