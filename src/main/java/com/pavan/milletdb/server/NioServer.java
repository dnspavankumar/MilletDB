package com.pavan.milletdb.server;

import com.pavan.milletdb.kvstore.ShardedKVStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Non-blocking NIO server for MilletDB.
 * Supports multiple concurrent clients using Selector and ServerSocketChannel.
 * Uses a worker thread pool to process commands asynchronously.
 * 
 * Supported commands:
 * - SET key value
 * - GET key
 * - DEL key
 * - EXPIRE key ttl
 * - STATS
 */
public class NioServer {
    
    private final ShardedKVStore<String, String> store;
    private final int port;
    private final int workerThreads;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private final AtomicBoolean running;
    private Thread serverThread;
    private ExecutorService workerPool;
    private RequestHandler requestHandler;
    
    // Statistics
    private final AtomicLong totalCommands;
    private final AtomicLong totalConnections;
    private final AtomicLong activeConnections;
    
    private static final int BUFFER_SIZE = 1024;
    private static final String DELIMITER = "\r\n";
    private static final int DEFAULT_WORKER_THREADS = 10;
    
    /**
     * Creates a new NIO server with default worker thread count.
     *
     * @param store the key-value store to use
     * @param port the port to listen on
     */
    public NioServer(ShardedKVStore<String, String> store, int port) {
        this(store, port, DEFAULT_WORKER_THREADS);
    }
    
    /**
     * Creates a new NIO server with custom worker thread count.
     *
     * @param store the key-value store to use
     * @param port the port to listen on
     * @param workerThreads number of worker threads for processing commands
     */
    public NioServer(ShardedKVStore<String, String> store, int port, int workerThreads) {
        this.store = store;
        this.port = port;
        this.workerThreads = workerThreads;
        this.running = new AtomicBoolean(false);
        this.totalCommands = new AtomicLong(0);
        this.totalConnections = new AtomicLong(0);
        this.activeConnections = new AtomicLong(0);
    }
    
    /**
     * Starts the server in a background thread.
     *
     * @throws IOException if server cannot be started
     */
    public void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Server is already running");
        }
        
        // Initialize worker pool
        workerPool = Executors.newFixedThreadPool(workerThreads, r -> {
            Thread thread = new Thread(r);
            thread.setName("Worker-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
        
        // Initialize request handler
        requestHandler = new RequestHandler(store, totalCommands, totalConnections, activeConnections);
        
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        
        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        running.set(true);
        
        serverThread = new Thread(this::run, "NioServer-" + port);
        serverThread.start();
    }
    
    /**
     * Stops the server gracefully.
     */
    public void stop() {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        if (selector != null) {
            selector.wakeup();
        }
        
        if (serverThread != null) {
            try {
                serverThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Shutdown worker pool
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        try {
            if (selector != null) {
                selector.close();
            }
            if (serverChannel != null) {
                serverChannel.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
    }
    
    /**
     * Main server loop.
     */
    private void run() {
        while (running.get()) {
            try {
                selector.select(1000);
                
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Error in server loop: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Handles new client connections.
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
            ClientContext context = new ClientContext();
            clientChannel.keyFor(selector).attach(context);
            
            totalConnections.incrementAndGet();
            activeConnections.incrementAndGet();
            
            sendResponse(clientChannel, "+OK Connected to MilletDB" + DELIMITER);
        }
    }
    
    /**
     * Handles reading data from clients.
     */
    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientContext context = (ClientContext) key.attachment();
        
        try {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            int bytesRead = clientChannel.read(buffer);
            
            if (bytesRead == -1) {
                closeClient(key);
                return;
            }
            
            if (bytesRead > 0) {
                buffer.flip();
                String data = StandardCharsets.UTF_8.decode(buffer).toString();
                context.appendData(data);
                
                // Process complete commands
                String commandLine;
                while ((commandLine = context.nextCommand()) != null) {
                    final String cmd = commandLine.trim();
                    // Submit to worker pool for processing
                    workerPool.submit(() -> processCommand(clientChannel, cmd));
                }
            }
        } catch (IOException e) {
            closeClient(key);
        }
    }
    
    /**
     * Processes a command in a worker thread and sends the response.
     */
    private void processCommand(SocketChannel clientChannel, String commandLine) {
        try {
            Response response = requestHandler.handleCommand(commandLine);
            sendResponse(clientChannel, response.serialize());
        } catch (Exception e) {
            try {
                sendResponse(clientChannel, Response.error(e.getMessage()).serialize());
            } catch (IOException ioException) {
                // Client disconnected, ignore
            }
        }
    }
    
    /**
     * Sends a response to the client.
     */
    private void sendResponse(SocketChannel channel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
    
    /**
     * Closes a client connection.
     */
    private void closeClient(SelectionKey key) {
        activeConnections.decrementAndGet();
        try {
            key.channel().close();
        } catch (IOException e) {
            // Ignore
        }
        key.cancel();
    }
    
    /**
     * Returns true if the server is running.
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Returns the port the server is listening on.
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Returns server statistics.
     */
    public ServerStats getStats() {
        return new ServerStats(
            totalConnections.get(),
            activeConnections.get(),
            totalCommands.get(),
            store.size()
        );
    }
    
    /**
     * Context for each client connection.
     */
    private static class ClientContext {
        private final StringBuilder buffer = new StringBuilder();
        
        void appendData(String data) {
            buffer.append(data);
        }
        
        String nextCommand() {
            int delimiterIndex = buffer.indexOf(DELIMITER);
            if (delimiterIndex == -1) {
                return null;
            }
            
            String command = buffer.substring(0, delimiterIndex);
            buffer.delete(0, delimiterIndex + DELIMITER.length());
            return command;
        }
    }
    
    /**
     * Server statistics snapshot.
     */
    public static class ServerStats {
        public final long totalConnections;
        public final long activeConnections;
        public final long totalCommands;
        public final int storeSize;
        
        public ServerStats(long totalConnections, long activeConnections, 
                          long totalCommands, int storeSize) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.totalCommands = totalCommands;
            this.storeSize = storeSize;
        }
    }
}
