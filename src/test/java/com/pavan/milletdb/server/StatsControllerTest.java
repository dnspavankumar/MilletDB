package com.pavan.milletdb.server;

import com.pavan.milletdb.kvstore.ShardedKVStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for StatsController REST endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.main.banner-mode=off",
    "logging.level.org.springframework=WARN"
})
class StatsControllerTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ShardedKVStore<String, String> store;
    
    @Test
    void testHealthEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/health", Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertTrue(body.containsKey("nettyServer"));
        assertTrue(body.containsKey("storeSize"));
        assertTrue(body.containsKey("timestamp"));
    }
    
    @Test
    void testStatsEndpoint() {
        // Add some data to the store first
        store.put("test-key-1", "test-value-1");
        store.put("test-key-2", "test-value-2");
        store.get("test-key-1"); // Generate some stats
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/stats", Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        
        // Verify main sections exist
        assertTrue(body.containsKey("server"));
        assertTrue(body.containsKey("store"));
        assertTrue(body.containsKey("shards"));
        assertTrue(body.containsKey("aggregated"));
        
        // Verify server section
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) body.get("server");
        assertTrue(server.containsKey("totalConnections"));
        assertTrue(server.containsKey("activeConnections"));
        assertTrue(server.containsKey("totalCommands"));
        assertTrue(server.containsKey("running"));
        assertTrue(server.containsKey("port"));
        
        // Verify store section
        @SuppressWarnings("unchecked")
        Map<String, Object> storeInfo = (Map<String, Object>) body.get("store");
        assertTrue(storeInfo.containsKey("size"));
        assertTrue(storeInfo.containsKey("capacity"));
        assertTrue(storeInfo.containsKey("numShards"));
        assertTrue(storeInfo.containsKey("capacityPerShard"));
        assertTrue(storeInfo.containsKey("utilizationPercent"));
        
        // Verify we have data
        assertTrue((Integer) storeInfo.get("size") >= 2);
        assertEquals(8, storeInfo.get("numShards"));
        assertEquals(10000, storeInfo.get("capacityPerShard"));
        assertEquals(80000, storeInfo.get("capacity"));
        
        // Verify shards section
        @SuppressWarnings("unchecked")
        Map<String, Object> shards = (Map<String, Object>) body.get("shards");
        assertEquals(8, shards.size()); // Should have 8 shards
        
        // Check first shard structure
        @SuppressWarnings("unchecked")
        Map<String, Object> shard0 = (Map<String, Object>) shards.get("0");
        assertNotNull(shard0);
        assertTrue(shard0.containsKey("size"));
        assertTrue(shard0.containsKey("capacity"));
        assertTrue(shard0.containsKey("gets"));
        assertTrue(shard0.containsKey("sets"));
        assertTrue(shard0.containsKey("deletes"));
        assertTrue(shard0.containsKey("expires"));
        assertTrue(shard0.containsKey("hits"));
        assertTrue(shard0.containsKey("misses"));
        assertTrue(shard0.containsKey("hitRate"));
        assertTrue(shard0.containsKey("evictions"));
        assertTrue(shard0.containsKey("expirations"));
        
        // Verify aggregated section
        @SuppressWarnings("unchecked")
        Map<String, Object> aggregated = (Map<String, Object>) body.get("aggregated");
        assertTrue(aggregated.containsKey("totalGets"));
        assertTrue(aggregated.containsKey("totalSets"));
        assertTrue(aggregated.containsKey("totalDeletes"));
        assertTrue(aggregated.containsKey("totalExpires"));
        assertTrue(aggregated.containsKey("totalOperations"));
        assertTrue(aggregated.containsKey("cacheHits"));
        assertTrue(aggregated.containsKey("cacheMisses"));
        assertTrue(aggregated.containsKey("hitRatePercent"));
        assertTrue(aggregated.containsKey("evictions"));
        assertTrue(aggregated.containsKey("expirations"));
        
        // Verify we have some operations recorded
        assertTrue((Integer) aggregated.get("totalSets") >= 2);
        assertTrue((Integer) aggregated.get("totalGets") >= 1);
    }
    
    @Test
    void testStatsEndpointWithMoreOperations() {
        // Perform various operations
        store.put("stats-test-1", "value1");
        store.put("stats-test-2", "value2");
        store.put("stats-test-3", "value3");
        
        store.get("stats-test-1"); // Hit
        store.get("stats-test-2"); // Hit
        store.get("nonexistent");  // Miss
        
        store.remove("stats-test-3");
        
        store.expire("stats-test-1", 10000);
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/stats", Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> aggregated = (Map<String, Object>) body.get("aggregated");
        
        // Should have recorded our operations
        assertTrue((Integer) aggregated.get("totalSets") >= 3);
        assertTrue((Integer) aggregated.get("totalGets") >= 3);
        assertTrue((Integer) aggregated.get("totalDeletes") >= 1);
        assertTrue((Integer) aggregated.get("totalExpires") >= 1);
        assertTrue((Integer) aggregated.get("cacheHits") >= 2);
        assertTrue((Integer) aggregated.get("cacheMisses") >= 1);
        
        // Hit rate should be reasonable
        Double hitRate = (Double) aggregated.get("hitRatePercent");
        assertTrue(hitRate >= 0.0 && hitRate <= 100.0);
    }
    
    @Test
    void testStatsEndpointResponseFormat() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/stats", String.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String jsonResponse = response.getBody();
        assertNotNull(jsonResponse);
        
        // Verify it's valid JSON with expected structure
        assertTrue(jsonResponse.contains("\"server\""));
        assertTrue(jsonResponse.contains("\"store\""));
        assertTrue(jsonResponse.contains("\"shards\""));
        assertTrue(jsonResponse.contains("\"aggregated\""));
    }
}
