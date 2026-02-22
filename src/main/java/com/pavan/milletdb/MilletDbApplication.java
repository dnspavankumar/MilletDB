package com.pavan.milletdb;

import com.pavan.milletdb.kvstore.ShardedKVStore;
import com.pavan.milletdb.server.NettyServer;
import com.pavan.milletdb.snapshot.SnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

@SpringBootApplication
public class MilletDbApplication implements CommandLineRunner, DisposableBean {

	private static final Logger logger = LoggerFactory.getLogger(MilletDbApplication.class);
	
	private static final int NUM_SHARDS = 8;
	private static final int CAPACITY_PER_SHARD = 10_000;
	private static final int MAX_KEY_BYTES = 4 * 1024;
	private static final int MAX_VALUE_BYTES = 1024 * 1024;
	private static final String SNAPSHOT_DIR = "./snapshots";

	@Value("${milletdb.tcp.enabled:true}")
	private boolean tcpEnabled;

	@Value("${milletdb.tcp.port:8080}")
	private int serverPort;

	@Value("${milletdb.tcp.workerThreads:20}")
	private int workerThreads;

	@Value("${milletdb.snapshot.enabled:true}")
	private boolean snapshotEnabled;

	@Value("${milletdb.snapshot.intervalSeconds:30}")
	private long snapshotIntervalSeconds;
	
	private NettyServer server;
	private SnapshotManager snapshotManager;
	private ShardedKVStore<String, String> store;

	public static void main(String[] args) {
		SpringApplication.run(MilletDbApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		printBanner();
		initializeStore();
		initializeSnapshotManager();
		if (tcpEnabled) {
			startServer();
		} else {
			logger.info("TCP server startup is disabled (milletdb.tcp.enabled=false)");
		}
		printStartupInfo();
		
		// Register shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
	}
	
	private void printBanner() {
		logger.info("=".repeat(60));
		logger.info("  __  __ _ _ _      _   ____  ____  ");
		logger.info(" |  \\/  (_) | | ___| |_|  _ \\| __ ) ");
		logger.info(" | |\\/| | | | |/ _ \\ __| | | |  _ \\ ");
		logger.info(" | |  | | | | |  __/ |_| |_| | |_) |");
		logger.info(" |_|  |_|_|_|_|\\___|\\__|____/|____/ ");
		logger.info("");
		logger.info("  High-Performance In-Memory Key-Value Store");
		logger.info("=".repeat(60));
	}
	
	private void initializeStore() {
		logger.info("Initializing ShardedKVStore...");
		logger.info("  - Number of shards: {}", NUM_SHARDS);
		logger.info("  - Capacity per shard: {}", CAPACITY_PER_SHARD);
		logger.info("  - Total capacity: {}", NUM_SHARDS * CAPACITY_PER_SHARD);
		
		store = new ShardedKVStore<>(NUM_SHARDS, CAPACITY_PER_SHARD, MAX_KEY_BYTES, MAX_VALUE_BYTES);
		logger.info("ShardedKVStore initialized successfully");
	}
	
	private void initializeSnapshotManager() throws IOException {
		if (!snapshotEnabled) {
			logger.info("Snapshot manager is disabled (milletdb.snapshot.enabled=false)");
			return;
		}

		logger.info("Initializing SnapshotManager...");
		logger.info("  - Snapshot directory: {}", SNAPSHOT_DIR);
		
		snapshotManager = new SnapshotManager(SNAPSHOT_DIR);
		
		// Try to load latest snapshot
		boolean loaded = snapshotManager.loadLatestSnapshot(store);
		if (loaded) {
			logger.info("Loaded latest snapshot successfully");
			logger.info("  - Store size after load: {}", store.size());
		} else {
			logger.info("No existing snapshot found, starting with empty store");
		}
		
		// Start periodic snapshots
		snapshotManager.startPeriodicSnapshots(store, snapshotIntervalSeconds);
		logger.info("Periodic snapshots enabled (interval: {} seconds)", snapshotIntervalSeconds);
	}
	
	private void startServer() throws IOException {
		logger.info("Starting Netty Server...");
		logger.info("  - Port: {}", serverPort);
		logger.info("  - Worker threads: {}", workerThreads);
		
		server = new NettyServer(store, serverPort, workerThreads);
		server.start();
		
		logger.info("Netty Server started successfully");
	}
	
	private void printStartupInfo() {
		logger.info("=".repeat(60));
		logger.info("MilletDB is ready to accept connections!");
		logger.info("");
		logger.info("Netty Server (TCP):");
		if (tcpEnabled) {
			logger.info("  - Address: localhost:{}", serverPort);
			logger.info("  - Protocol: Text-based (Redis-like)");
			logger.info("  - Connect: telnet localhost {} or nc localhost {}", serverPort, serverPort);
		} else {
			logger.info("  - Disabled (milletdb.tcp.enabled=false)");
		}
		logger.info("");
		logger.info("REST API (HTTP):");
		logger.info("  - Address: http://localhost:8081");
		logger.info("  - Endpoints:");
		logger.info("    GET /stats  - Live metrics in JSON");
		logger.info("    GET /health - Health check");
		logger.info("");
		logger.info("Supported Commands (TCP):");
		logger.info("  - SET key value    : Store a key-value pair");
		logger.info("  - GET key          : Retrieve a value");
		logger.info("  - DEL key          : Delete a key");
		logger.info("  - EXPIRE key ttl   : Set TTL in milliseconds");
		logger.info("  - STATS            : Show server statistics");
		logger.info("  - PING             : Test connection");
		logger.info("=".repeat(60));
	}
	
	@Override
	public void destroy() {
		shutdown();
	}
	
	private void shutdown() {
		logger.info("=".repeat(60));
		logger.info("Shutting down MilletDB...");
		
		// Stop server
		if (server != null && server.isRunning()) {
			logger.info("Stopping Netty Server...");
			server.stop();
			logger.info("Netty Server stopped");
		}
		
		// Save final snapshot
		if (snapshotManager != null && store != null) {
			try {
				logger.info("Saving final snapshot...");
				snapshotManager.stopPeriodicSnapshots();
				snapshotManager.saveSnapshot(store);
				logger.info("Final snapshot saved successfully");
				logger.info("  - Store size: {}", store.size());
			} catch (IOException e) {
				logger.error("Failed to save final snapshot: {}", e.getMessage());
			}
		}
		
		// Print final statistics
		if (server != null) {
			NettyServer.ServerStats stats = server.getStats();
			logger.info("");
			logger.info("Final Statistics:");
			logger.info("  - Total connections: {}", stats.totalConnections);
			logger.info("  - Total commands: {}", stats.totalCommands);
			logger.info("  - Final store size: {}", stats.storeSize);
		}
		
		logger.info("");
		logger.info("MilletDB shutdown complete. Goodbye!");
		logger.info("=".repeat(60));
	}
	
	@Bean
	public ShardedKVStore<String, String> shardedKVStore() {
		if (store == null) {
			store = new ShardedKVStore<>(NUM_SHARDS, CAPACITY_PER_SHARD, MAX_KEY_BYTES, MAX_VALUE_BYTES);
		}
		return store;
	}
	
	@Bean
	public NettyServer nettyServer() {
		if (server == null) {
			server = new NettyServer(shardedKVStore(), serverPort, workerThreads);
		}
		return server;
	}
}

