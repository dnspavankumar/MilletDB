package com.pavan.milletdb.server;

import com.pavan.milletdb.kvstore.ShardedKVStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NioServerTest {
    
    private NioServer server;
    private ShardedKVStore<String, String> store;
    private static final int TEST_PORT = 9999;
    
    @BeforeEach
    void setUp() throws IOException {
        store = new ShardedKVStore<>(4, 100);
        server = new NioServer(store, TEST_PORT);
        server.start();
        
        // Wait for server to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }
    
    @Test
    void testServerStartsAndStops() {
        assertTrue(server.isRunning());
        assertEquals(TEST_PORT, server.getPort());
        
        server.stop();
        assertFalse(server.isRunning());
    }
    
    @Test
    void testSetCommand() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("SET key1 value1");
            assertEquals("+OK", response);
            
            assertEquals("value1", store.get("key1"));
        }
    }
    
    @Test
    void testGetCommand() throws IOException {
        store.put("key1", "value1");
        
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("GET key1");
            assertTrue(response.startsWith("$6"));
            String value = client.readLine();
            assertEquals("value1", value);
        }
    }
    
    @Test
    void testGetNonExistentKey() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("GET nonexistent");
            assertEquals("$-1", response);
        }
    }
    
    @Test
    void testDelCommand() throws IOException {
        store.put("key1", "value1");
        
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("DEL key1");
            assertEquals(":1", response);
            
            assertNull(store.get("key1"));
        }
    }
    
    @Test
    void testDelNonExistentKey() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("DEL nonexistent");
            assertEquals(":0", response);
        }
    }
    
    @Test
    void testExpireCommand() throws IOException, InterruptedException {
        store.put("key1", "value1");
        
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("EXPIRE key1 100");
            assertEquals(":1", response);
            
            Thread.sleep(150);
            assertNull(store.get("key1"));
        }
    }
    
    @Test
    void testExpireNonExistentKey() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("EXPIRE nonexistent 1000");
            assertEquals(":0", response);
        }
    }
    
    @Test
    void testExpireWithInvalidTTL() throws IOException {
        store.put("key1", "value1");
        
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("EXPIRE key1 -100");
            assertTrue(response.startsWith("-ERR"));
        }
    }
    
    @Test
    void testStatsCommand() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("STATS");
            assertNotNull(response);
            assertTrue(response.contains("Statistics") || response.contains("total_connections"),
                "Response was: " + response);
            
            // Read remaining lines if multi-line response
            StringBuilder fullResponse = new StringBuilder(response);
            String line;
            while ((line = client.readLineNonBlocking()) != null) {
                fullResponse.append("\n").append(line);
            }
            
            String full = fullResponse.toString();
            assertTrue(full.contains("total_connections"));
        }
    }
    
    @Test
    void testPingCommand() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("PING");
            assertEquals("+PONG", response);
        }
    }
    
    @Test
    void testUnknownCommand() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("UNKNOWN");
            assertTrue(response.startsWith("-ERR"));
            assertTrue(response.contains("Unknown command"));
        }
    }
    
    @Test
    void testSetWithoutValue() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("SET key1");
            assertTrue(response.startsWith("-ERR"));
        }
    }
    
    @Test
    void testGetWithoutKey() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("GET");
            assertTrue(response.startsWith("-ERR"));
        }
    }
    
    @Test
    void testMultipleCommands() throws IOException {
        try (TestClient client = new TestClient()) {
            client.sendCommand("SET key1 value1");
            client.sendCommand("SET key2 value2");
            client.sendCommand("SET key3 value3");
            
            String response = client.sendCommand("GET key2");
            assertTrue(response.startsWith("$6"));
            assertEquals("value2", client.readLine());
        }
    }
    
    @Test
    void testMultipleClients() throws InterruptedException {
        int numClients = 10;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient()) {
                    String key = "key-" + clientId;
                    String value = "value-" + clientId;
                    
                    String setResponse = client.sendCommand("SET " + key + " " + value);
                    assertEquals("+OK", setResponse);
                    
                    String getResponse = client.sendCommand("GET " + key);
                    assertTrue(getResponse.startsWith("$"));
                    assertEquals(value, client.readLine());
                } catch (IOException e) {
                    fail("Client " + clientId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
    }
    
    @Test
    void testConcurrentOperations() throws InterruptedException {
        int numThreads = 20;
        int numOperations = 50;
        CountDownLatch latch = new CountDownLatch(numThreads);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient()) {
                    for (int j = 0; j < numOperations; j++) {
                        String key = "key-" + threadId + "-" + j;
                        String value = "value-" + j;
                        
                        client.sendCommand("SET " + key + " " + value);
                        client.sendCommand("GET " + key);
                        
                        if (j % 5 == 0) {
                            client.sendCommand("DEL " + key);
                        }
                    }
                } catch (IOException e) {
                    fail("Thread " + threadId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        NioServer.ServerStats stats = server.getStats();
        assertTrue(stats.totalCommands > 0);
        assertTrue(stats.totalConnections >= numThreads);
    }
    
    @Test
    void testSetWithSpacesInValue() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("SET mykey hello world");
            assertEquals("+OK", response);
            
            String getResponse = client.sendCommand("GET mykey");
            assertTrue(getResponse.startsWith("$"));
            assertEquals("hello world", client.readLine());
        }
    }
    
    @Test
    void testServerStats() throws IOException {
        try (TestClient client1 = new TestClient();
             TestClient client2 = new TestClient()) {
            
            client1.sendCommand("SET key1 value1");
            client2.sendCommand("SET key2 value2");
            
            NioServer.ServerStats stats = server.getStats();
            assertTrue(stats.totalConnections >= 2);
            assertTrue(stats.activeConnections >= 2);
            assertTrue(stats.totalCommands >= 2);
            assertTrue(stats.storeSize >= 2);
        }
    }
    
    @Test
    void testEmptyCommand() throws IOException {
        try (TestClient client = new TestClient()) {
            String response = client.sendCommand("");
            assertTrue(response.startsWith("-ERR"));
        }
    }
    
    @Test
    void testCaseInsensitiveCommands() throws IOException {
        try (TestClient client = new TestClient()) {
            client.sendCommand("set key1 value1");
            String response = client.sendCommand("get key1");
            assertTrue(response.startsWith("$"));
            assertEquals("value1", client.readLine());
        }
    }
    
    /**
     * Helper class for testing client connections.
     */
    private static class TestClient implements AutoCloseable {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        
        TestClient() throws IOException {
            socket = new Socket("localhost", TEST_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Read welcome message
            in.readLine();
        }
        
        String sendCommand(String command) throws IOException {
            out.print(command + "\r\n");
            out.flush();
            return in.readLine();
        }
        
        String readLine() throws IOException {
            return in.readLine();
        }
        
        String readLineNonBlocking() throws IOException {
            if (in.ready()) {
                return in.readLine();
            }
            return null;
        }
        
        @Override
        public void close() throws IOException {
            in.close();
            out.close();
            socket.close();
        }
    }
}
