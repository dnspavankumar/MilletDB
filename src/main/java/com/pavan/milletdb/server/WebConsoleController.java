package com.pavan.milletdb.server;

import com.pavan.milletdb.kvstore.ShardedKVStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for web console to interact with MilletDB.
 * Provides HTTP endpoints that execute commands on the key-value store.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class WebConsoleController {
    
    private final ShardedKVStore<String, String> store;
    
    @Autowired
    public WebConsoleController(ShardedKVStore<String, String> store) {
        this.store = store;
    }
    
    /**
     * SET command - Store a key-value pair
     */
    @PostMapping("/set")
    public Map<String, Object> set(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String key = request.get("key");
            String value = request.get("value");
            
            if (key == null || value == null) {
                response.put("success", false);
                response.put("message", "Key and value are required");
                return response;
            }
            
            store.put(key, value);
            response.put("success", true);
            response.put("message", "OK");
            response.put("command", "SET " + key + " " + value);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }
    
    /**
     * GET command - Retrieve a value
     */
    @GetMapping("/get/{key}")
    public Map<String, Object> get(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();
        try {
            String value = store.get(key);
            
            response.put("success", true);
            response.put("key", key);
            response.put("value", value);
            response.put("found", value != null);
            response.put("command", "GET " + key);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }
    
    /**
     * DEL command - Delete a key
     */
    @DeleteMapping("/del/{key}")
    public Map<String, Object> delete(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();
        try {
            store.remove(key);
            
            response.put("success", true);
            response.put("message", "OK");
            response.put("command", "DEL " + key);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }
    
    /**
     * EXPIRE command - Set TTL on a key
     */
    @PostMapping("/expire")
    public Map<String, Object> expire(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String key = (String) request.get("key");
            Integer ttl = (Integer) request.get("ttl");
            
            if (key == null || ttl == null) {
                response.put("success", false);
                response.put("message", "Key and TTL are required");
                return response;
            }
            
            store.expire(key, ttl);
            
            response.put("success", true);
            response.put("message", "OK");
            response.put("command", "EXPIRE " + key + " " + ttl);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }
    
    /**
     * PING command - Test connection
     */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "PONG");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
