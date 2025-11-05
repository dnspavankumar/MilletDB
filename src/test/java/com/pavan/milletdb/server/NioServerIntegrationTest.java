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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for NioServer with multiple concurrent clients.
 */
class NioServerIntegrationTest {
    
    private NioServer server;
    private ShardedKVStore<String, String> store;
    private static final int TEST_PORT = 9998;
    private static final int WORKER_THREADS = 20;
    
    @BeforeEach
    void setUp() throws IOException {
        store = new ShardedKVStore<>(8, 1000);
        server = new NioServer(store, TEST_PORT, WORKER_THREADS);
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
    void testMultipleClientsSequentialOperations() throws InterruptedException {
        int numClients = 10;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        List<String> errors = new CopyOnWriteArrayList<>();
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    // Each client performs a sequence of operations
                    String key = "client-" + clientId;
                    String value = "value-" + clientId;
                    
                    // SET
                    String setResponse = client.sendCommand("SET " + key + " " + value);
                    assertEquals("+OK", setResponse, "Client " + clientId + " SET failed");
                    
                    // GET
                    String getResponse = client.sendCommand("GET " + key);
                    assertTrue(getResponse.startsWith("$"), "Client " + clientId + " GET failed");
                    String retrievedValue = client.readLine();
                    assertEquals(value, retrievedValue, "Client " + clientId + " value mismatch");
                    
                    // DEL
                    String delResponse = client.sendCommand("DEL " + key);
                    assertEquals(":1", delResponse, "Client " + clientId + " DEL failed");
                    
                } catch (Exception e) {
                    errors.add("Client " + clientId + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Clients did not complete in time");
        executor.shutdown();
        
        if (!errors.isEmpty()) {
            fail("Errors occurred: " + String.join(", ", errors));
        }
    }
    
    @Test
    void testConcurrentWritesToSameKey() throws InterruptedException {
        int numClients = 20;
        String sharedKey = "shared-key";
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    String value = "value-" + clientId;
                    String response = client.sendCommand("SET " + sharedKey + " " + value);
                    
                    if ("+OK".equals(response)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        // All writes should succeed
        assertEquals(numClients, successCount.get());
        
        // Final value should be from one of the clients
        assertNotNull(store.get(sharedKey));
    }
    
    @Test
    void testConcurrentReadsAndWrites() throws InterruptedException {
        int numClients = 30;
        int operationsPerClient = 50;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        AtomicInteger totalOperations = new AtomicInteger(0);
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    
                    for (int j = 0; j < operationsPerClient; j++) {
                        String key = "key-" + random.nextInt(100);
                        
                        if (random.nextBoolean()) {
                            // Write operation
                            String value = "value-" + clientId + "-" + j;
                            client.sendCommand("SET " + key + " " + value);
                        } else {
                            // Read operation
                            client.sendCommand("GET " + key);
                            // Read response
                            String response = client.readLineNonBlocking();
                        }
                        
                        totalOperations.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Verify total operations
        assertEquals(numClients * operationsPerClient, totalOperations.get());
        
        // Verify server stats
        NioServer.ServerStats stats = server.getStats();
        assertTrue(stats.totalCommands >= numClients * operationsPerClient);
    }
    
    @Test
    void testConcurrentExpireOperations() throws InterruptedException {
        int numClients = 15;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        
        // Pre-populate keys
        for (int i = 0; i < numClients; i++) {
            store.put("expire-key-" + i, "value-" + i);
        }
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    String key = "expire-key-" + clientId;
                    
                    // Set expiration
                    String response = client.sendCommand("EXPIRE " + key + " 100");
                    assertEquals(":1", response);
                    
                    // Verify key still exists
                    String getResponse = client.sendCommand("GET " + key);
                    assertTrue(getResponse.startsWith("$"));
                    
                } catch (Exception e) {
                    fail("Client " + clientId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Wait for expiration
        Thread.sleep(150);
        
        // Verify all keys expired
        for (int i = 0; i < numClients; i++) {
            assertNull(store.get("expire-key-" + i));
        }
    }
    
    @Test
    void testHighVolumeOperations() throws InterruptedException {
        int numClients = 50;
        int operationsPerClient = 100;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    for (int j = 0; j < operationsPerClient; j++) {
                        String key = "bulk-" + clientId + "-" + j;
                        String value = "data-" + j;
                        
                        String response = client.sendCommand("SET " + key + " " + value);
                        if ("+OK".equals(response)) {
                            successfulOperations.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Most operations should succeed (some might be evicted due to capacity)
        assertTrue(successfulOperations.get() >= numClients * operationsPerClient * 0.9);
    }
    
    @Test
    void testClientDisconnectionHandling() throws InterruptedException {
        int numClients = 20;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try {
                    TestClient client = new TestClient(TEST_PORT);
                    
                    // Perform some operations
                    client.sendCommand("SET key-" + clientId + " value");
                    client.sendCommand("GET key-" + clientId);
                    
                    // Abruptly close connection
                    client.close();
                    
                } catch (Exception e) {
                    // Expected
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Server should still be running
        assertTrue(server.isRunning());
    }
    
    @Test
    void testMixedCommandTypes() throws InterruptedException {
        int numClients = 25;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    // SET
                    client.sendCommand("SET key-" + clientId + " value-" + clientId);
                    
                    // GET
                    client.sendCommand("GET key-" + clientId);
                    client.readLine(); // Read value
                    
                    // PING
                    String pingResponse = client.sendCommand("PING");
                    assertEquals("+PONG", pingResponse);
                    
                    // STATS
                    client.sendCommand("STATS");
                    // Read multi-line response
                    while (client.readLineNonBlocking() != null) {
                        // Consume response
                    }
                    
                    // DEL
                    String delResponse = client.sendCommand("DEL key-" + clientId);
                    assertEquals(":1", delResponse);
                    
                } catch (Exception e) {
                    fail("Client " + clientId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
    }
    
    @Test
    void testLongRunningConnections() throws InterruptedException {
        int numClients = 10;
        int duration = 5; // seconds
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    long endTime = System.currentTimeMillis() + (duration * 1000);
                    int operationCount = 0;
                    
                    while (System.currentTimeMillis() < endTime) {
                        String key = "long-" + clientId + "-" + operationCount;
                        client.sendCommand("SET " + key + " value");
                        client.sendCommand("GET " + key);
                        client.readLine(); // Read value
                        
                        operationCount++;
                        Thread.sleep(50);
                    }
                    
                    assertTrue(operationCount > 0);
                    
                } catch (Exception e) {
                    fail("Client " + clientId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(duration + 10, TimeUnit.SECONDS));
        executor.shutdown();
    }
    
    @Test
    void testStressTestWithRandomOperations() throws InterruptedException {
        int numClients = 40;
        int operationsPerClient = 50;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        AtomicInteger errors = new AtomicInteger(0);
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    
                    for (int j = 0; j < operationsPerClient; j++) {
                        int operation = random.nextInt(5);
                        String key = "stress-" + random.nextInt(50);
                        
                        try {
                            switch (operation) {
                                case 0: // SET
                                    client.sendCommand("SET " + key + " value-" + clientId);
                                    break;
                                case 1: // GET
                                    client.sendCommand("GET " + key);
                                    client.readLineNonBlocking();
                                    break;
                                case 2: // DEL
                                    client.sendCommand("DEL " + key);
                                    break;
                                case 3: // EXPIRE
                                    client.sendCommand("EXPIRE " + key + " 1000");
                                    break;
                                case 4: // PING
                                    client.sendCommand("PING");
                                    break;
                            }
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Allow some errors but not too many
        assertTrue(errors.get() < numClients * operationsPerClient * 0.1,
            "Too many errors: " + errors.get());
    }
    
    @Test
    void testServerStatsAccuracy() throws InterruptedException, IOException {
        int numClients = 15;
        int commandsPerClient = 10;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        
        for (int i = 0; i < numClients; i++) {
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    for (int j = 0; j < commandsPerClient; j++) {
                        client.sendCommand("SET key-" + j + " value");
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Give server time to process
        Thread.sleep(500);
        
        NioServer.ServerStats stats = server.getStats();
        
        // Verify stats
        assertTrue(stats.totalConnections >= numClients);
        assertTrue(stats.totalCommands >= numClients * commandsPerClient);
        assertTrue(stats.storeSize > 0);
    }
    
    @Test
    void testConcurrentStatsRequests() throws InterruptedException {
        int numClients = 20;
        CountDownLatch latch = new CountDownLatch(numClients);
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numClients; i++) {
            executor.submit(() -> {
                try (TestClient client = new TestClient(TEST_PORT)) {
                    String response = client.sendCommand("STATS");
                    
                    if (response != null && response.contains("Statistics")) {
                        successCount.incrementAndGet();
                    }
                    
                    // Consume remaining lines
                    while (client.readLineNonBlocking() != null) {
                        // Consume
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        // All STATS requests should succeed
        assertEquals(numClients, successCount.get());
    }
    
    /**
     * Helper class for testing client connections.
     */
    private static class TestClient implements AutoCloseable {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        
        TestClient(int port) throws IOException {
            socket = new Socket("localhost", port);
            socket.setSoTimeout(5000); // 5 second timeout
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
