package com.pavan.milletdb.server;

import com.pavan.milletdb.kvstore.ConcurrentKVStore;
import com.pavan.milletdb.kvstore.ShardedKVStore;
import com.pavan.milletdb.metrics.StatsCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for exposing live metrics.
 * Provides a thin wrapper around StatsCollector without modifying core logic.
 */
@RestController
public class StatsController {
    
    private final ShardedKVStore<String, String> store;
    private final NettyServer nettyServer;
    
    @Autowired
    public StatsController(ShardedKVStore<String, String> store, NettyServer nettyServer) {
        this.store = store;
        this.nettyServer = nettyServer;
    }
    
    /**
     * GET /stats - Returns live metrics in JSON format
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> response = new HashMap<>();
        
        // Server information
        response.put("server", getServerInfo());
        
        // Store information
        response.put("store", getStoreInfo());
        
        // Per-shard statistics
        response.put("shards", getShardStats());
        
        // Aggregated statistics
        response.put("aggregated", getAggregatedStats());
        
        return response;
    }
    
    /**
     * GET /health - Simple health check endpoint
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("nettyServer", nettyServer.isRunning() ? "RUNNING" : "STOPPED");
        response.put("storeSize", store.size());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
    
    private Map<String, Object> getServerInfo() {
        Map<String, Object> serverInfo = new HashMap<>();
        
        NettyServer.ServerStats serverStats = nettyServer.getStats();
        serverInfo.put("totalConnections", serverStats.totalConnections);
        serverInfo.put("activeConnections", serverStats.activeConnections);
        serverInfo.put("totalCommands", serverStats.totalCommands);
        serverInfo.put("running", nettyServer.isRunning());
        serverInfo.put("port", nettyServer.getPort());
        
        return serverInfo;
    }
    
    private Map<String, Object> getStoreInfo() {
        Map<String, Object> storeInfo = new HashMap<>();
        
        storeInfo.put("size", store.size());
        storeInfo.put("capacity", store.getTotalCapacity());
        storeInfo.put("numShards", store.getNumShards());
        storeInfo.put("capacityPerShard", store.getCapacityPerShard());
        
        // Calculate utilization
        double utilization = (double) store.size() / store.getTotalCapacity() * 100;
        storeInfo.put("utilizationPercent", Math.round(utilization * 100.0) / 100.0);
        
        return storeInfo;
    }
    
    private Map<Integer, Map<String, Object>> getShardStats() {
        Map<Integer, Map<String, Object>> shardsInfo = new HashMap<>();
        
        ConcurrentKVStore<String, String>[] shards = store.getShards();
        for (int i = 0; i < shards.length; i++) {
            Map<String, Object> shardInfo = new HashMap<>();
            StatsCollector stats = shards[i].getStats();
            
            shardInfo.put("size", shards[i].size());
            shardInfo.put("capacity", shards[i].getCapacity());
            shardInfo.put("gets", stats.getTotalGets());
            shardInfo.put("sets", stats.getTotalSets());
            shardInfo.put("deletes", stats.getTotalDeletes());
            shardInfo.put("expires", stats.getTotalExpires());
            shardInfo.put("hits", stats.getCacheHits());
            shardInfo.put("misses", stats.getCacheMisses());
            shardInfo.put("hitRate", Math.round(stats.getHitRate() * 10000.0) / 100.0);
            shardInfo.put("evictions", stats.getEvictions());
            shardInfo.put("expirations", stats.getExpirations());
            
            shardsInfo.put(i, shardInfo);
        }
        
        return shardsInfo;
    }
    
    private Map<String, Object> getAggregatedStats() {
        Map<String, Object> aggregated = new HashMap<>();
        
        // Aggregate stats from all shards
        long totalGets = 0;
        long totalSets = 0;
        long totalDeletes = 0;
        long totalExpires = 0;
        long totalHits = 0;
        long totalMisses = 0;
        long totalEvictions = 0;
        long totalExpirations = 0;
        
        ConcurrentKVStore<String, String>[] shards = store.getShards();
        for (ConcurrentKVStore<String, String> shard : shards) {
            StatsCollector stats = shard.getStats();
            totalGets += stats.getTotalGets();
            totalSets += stats.getTotalSets();
            totalDeletes += stats.getTotalDeletes();
            totalExpires += stats.getTotalExpires();
            totalHits += stats.getCacheHits();
            totalMisses += stats.getCacheMisses();
            totalEvictions += stats.getEvictions();
            totalExpirations += stats.getExpirations();
        }
        
        aggregated.put("totalGets", totalGets);
        aggregated.put("totalSets", totalSets);
        aggregated.put("totalDeletes", totalDeletes);
        aggregated.put("totalExpires", totalExpires);
        aggregated.put("totalOperations", totalGets + totalSets + totalDeletes + totalExpires);
        aggregated.put("cacheHits", totalHits);
        aggregated.put("cacheMisses", totalMisses);
        
        // Calculate overall hit rate
        long totalAccesses = totalHits + totalMisses;
        double hitRate = totalAccesses == 0 ? 0.0 : (double) totalHits / totalAccesses;
        aggregated.put("hitRatePercent", Math.round(hitRate * 10000.0) / 100.0);
        
        aggregated.put("evictions", totalEvictions);
        aggregated.put("expirations", totalExpirations);
        
        return aggregated;
    }
}
