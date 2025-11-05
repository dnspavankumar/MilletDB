package com.pavan.milletdb.server;

import com.pavan.milletdb.kvstore.ShardedKVStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RequestHandlerTest {
    
    private RequestHandler handler;
    private ShardedKVStore<String, String> store;
    private AtomicLong totalCommands;
    private AtomicLong totalConnections;
    private AtomicLong activeConnections;
    
    @BeforeEach
    void setUp() {
        store = new ShardedKVStore<>(4, 100);
        totalCommands = new AtomicLong(0);
        totalConnections = new AtomicLong(0);
        activeConnections = new AtomicLong(0);
        handler = new RequestHandler(store, totalCommands, totalConnections, activeConnections);
    }
    
    @Test
    void testHandleSetCommand() {
        Response response = handler.handleCommand("SET key1 value1");
        
        assertEquals(Response.ResponseType.SIMPLE_STRING, response.getType());
        assertEquals("OK", response.getData());
        assertEquals("value1", store.get("key1"));
        assertEquals(1, totalCommands.get());
    }
    
    @Test
    void testHandleGetCommand() {
        store.put("key1", "value1");
        
        Response response = handler.handleCommand("GET key1");
        
        assertEquals(Response.ResponseType.BULK_STRING, response.getType());
        assertEquals("value1", response.getData());
    }
    
    @Test
    void testHandleGetNonExistentKey() {
        Response response = handler.handleCommand("GET nonexistent");
        
        assertEquals(Response.ResponseType.NULL_BULK_STRING, response.getType());
    }
    
    @Test
    void testHandleDelCommand() {
        store.put("key1", "value1");
        
        Response response = handler.handleCommand("DEL key1");
        
        assertEquals(Response.ResponseType.INTEGER, response.getType());
        assertEquals("1", response.getData());
        assertNull(store.get("key1"));
    }
    
    @Test
    void testHandleDelNonExistentKey() {
        Response response = handler.handleCommand("DEL nonexistent");
        
        assertEquals(Response.ResponseType.INTEGER, response.getType());
        assertEquals("0", response.getData());
    }
    
    @Test
    void testHandleExpireCommand() {
        store.put("key1", "value1");
        
        Response response = handler.handleCommand("EXPIRE key1 1000");
        
        assertEquals(Response.ResponseType.INTEGER, response.getType());
        assertEquals("1", response.getData());
    }
    
    @Test
    void testHandleExpireNonExistentKey() {
        Response response = handler.handleCommand("EXPIRE nonexistent 1000");
        
        assertEquals(Response.ResponseType.INTEGER, response.getType());
        assertEquals("0", response.getData());
    }
    
    @Test
    void testHandleExpireWithInvalidTTL() {
        store.put("key1", "value1");
        
        Response response = handler.handleCommand("EXPIRE key1 -100");
        
        assertEquals(Response.ResponseType.ERROR, response.getType());
        assertTrue(response.getData().contains("positive"));
    }
    
    @Test
    void testHandleExpireWithNonNumericTTL() {
        store.put("key1", "value1");
        
        Response response = handler.handleCommand("EXPIRE key1 abc");
        
        assertEquals(Response.ResponseType.ERROR, response.getType());
        assertTrue(response.getData().contains("Invalid TTL"));
    }
    
    @Test
    void testHandleStatsCommand() {
        Response response = handler.handleCommand("STATS");
        
        assertEquals(Response.ResponseType.MULTI_LINE, response.getType());
        assertTrue(response.getData().contains("Server Statistics"));
        assertTrue(response.getData().contains("total_commands"));
    }
    
    @Test
    void testHandlePingCommand() {
        Response response = handler.handleCommand("PING");
        
        assertEquals(Response.ResponseType.SIMPLE_STRING, response.getType());
        assertEquals("PONG", response.getData());
    }
    
    @Test
    void testHandleQuitCommand() {
        Response response = handler.handleCommand("QUIT");
        
        assertEquals(Response.ResponseType.SIMPLE_STRING, response.getType());
        assertEquals("Goodbye", response.getData());
    }
    
    @Test
    void testHandleUnknownCommand() {
        Response response = handler.handleCommand("UNKNOWN arg1");
        
        assertEquals(Response.ResponseType.ERROR, response.getType());
        assertTrue(response.getData().contains("Unknown command"));
    }
    
    @Test
    void testHandleEmptyCommand() {
        Response response = handler.handleCommand("");
        
        assertEquals(Response.ResponseType.ERROR, response.getType());
        assertTrue(response.getData().contains("Empty command"));
    }
    
    @Test
    void testHandleSetWithoutValue() {
        Response response = handler.handleCommand("SET key1");
        
        assertEquals(Response.ResponseType.ERROR, response.getType());
        assertTrue(response.getData().contains("requires key and value"));
    }
    
    @Test
    void testHandleGetWithoutKey() {
        Response response = handler.handleCommand("GET");
        
        assertEquals(Response.ResponseType.ERROR, response.getType());
        assertTrue(response.getData().contains("requires key"));
    }
    
    @Test
    void testHandleSetWithSpacesInValue() {
        Response response = handler.handleCommand("SET mykey hello world");
        
        assertEquals(Response.ResponseType.SIMPLE_STRING, response.getType());
        assertEquals("hello world", store.get("mykey"));
    }
    
    @Test
    void testCommandCounterIncrement() {
        assertEquals(0, totalCommands.get());
        
        handler.handleCommand("SET key1 value1");
        assertEquals(1, totalCommands.get());
        
        handler.handleCommand("GET key1");
        assertEquals(2, totalCommands.get());
        
        handler.handleCommand("DEL key1");
        assertEquals(3, totalCommands.get());
    }
    
    @Test
    void testMultipleCommands() {
        handler.handleCommand("SET key1 value1");
        handler.handleCommand("SET key2 value2");
        handler.handleCommand("SET key3 value3");
        
        Response response = handler.handleCommand("GET key2");
        assertEquals("value2", response.getData());
        
        assertEquals(4, totalCommands.get());
    }
}
