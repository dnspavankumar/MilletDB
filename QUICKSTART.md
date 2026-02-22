# MilletDB Quick Start Guide

## Prerequisites

- Java 17 or higher
- Maven (included via wrapper)

## Installation

1. Clone or download the MilletDB project
2. Navigate to the project directory

```bash
cd MilletDB
```

## Starting the Server

### Option 1: Using Startup Scripts (Recommended)

**Linux/Mac:**
```bash
chmod +x start-server.sh
./start-server.sh
```

**Windows:**
```cmd
start-server.cmd
```

### Option 2: Using Maven Directly

```bash
./mvnw spring-boot:run
```

### Option 3: Build and Run JAR

```bash
./mvnw clean package
java -jar target/MilletDB-0.0.1-SNAPSHOT.jar
```

## Connecting to the Server

Once the server is running on port 8080, connect using:

### Using Telnet
```bash
telnet localhost 8080
```

### Using Netcat
```bash
nc localhost 8080
```

### Using a Custom Client
See [CLIENT_EXAMPLE.md](CLIENT_EXAMPLE.md) for code examples.

## Basic Commands

After connecting, try these commands:

```
SET mykey myvalue
+OK

GET mykey
$7
myvalue

EXPIRE mykey 5000
:1

DEL mykey
:1

STATS
# Server Statistics
total_connections:1
active_connections:1
total_commands:4
store_size:0
store_capacity:80000
num_shards:8

PING
+PONG
```

## Configuration

Default configuration (in `MilletDbApplication.java`):

- **Port**: 8080
- **Shards**: 8
- **Capacity per shard**: 10,000
- **Total capacity**: 80,000 entries
- **Worker threads**: 20
- **Snapshot interval**: 30 seconds
- **Snapshot directory**: `./snapshots/`

To modify these settings, edit the constants in `MilletDbApplication.java`:

```java
private static final int NUM_SHARDS = 8;
private static final int CAPACITY_PER_SHARD = 10_000;
private static final int SERVER_PORT = 8080;
private static final int WORKER_THREADS = 20;
private static final String SNAPSHOT_DIR = "./snapshots";
```

## Testing

Run the comprehensive test suite:

```bash
./mvnw test
```

Expected output:
```
Tests run: 133, Failures: 0, Errors: 0, Skipped: 0
```

## Benchmarking

Run performance benchmarks:

```bash
./mvnw test-compile exec:java \
  -Dexec.mainClass="com.pavan.milletdb.kvstore.ShardedKVStoreBenchmark" \
  -Dexec.classpathScope=test
```

## Stopping the Server

Press `Ctrl+C` to trigger graceful shutdown. The server will:
1. Stop accepting new connections
2. Save a final snapshot
3. Print statistics
4. Clean up resources

Example shutdown output:
```
============================================================
Shutting down MilletDB...
Stopping Netty Server...
Netty Server stopped
Saving final snapshot...
Final snapshot saved successfully
  - Store size: 1234

Final Statistics:
  - Total connections: 42
  - Total commands: 5678
  - Final store size: 1234

MilletDB shutdown complete. Goodbye!
============================================================
```

## Persistence

MilletDB automatically saves snapshots every 30 seconds to `./snapshots/`.

On startup, it will automatically load the most recent snapshot if available.

Snapshot files are named: `snapshot-<timestamp>.json`

### Manual Snapshot Management

To manually manage snapshots, use the `SnapshotManager` API:

```java
SnapshotManager manager = new SnapshotManager("./snapshots");

// Save snapshot
manager.saveSnapshot(store);

// Load latest
manager.loadLatestSnapshot(store);

// Cleanup old snapshots (keep last 5)
manager.cleanupOldSnapshots(5);
```

## Troubleshooting

### Port Already in Use

If port 8080 is already in use, change `SERVER_PORT` in `MilletDbApplication.java`.

### Connection Refused

Ensure the server is running and listening on the correct port:
```bash
netstat -an | grep 8080
```

### Out of Memory

If you encounter memory issues with large datasets:
1. Reduce `CAPACITY_PER_SHARD` or `NUM_SHARDS`
2. Increase JVM heap size:
   ```bash
   export MAVEN_OPTS="-Xmx2g"
   ./mvnw spring-boot:run
   ```

### Snapshot Directory Permissions

Ensure the application has write permissions to the snapshot directory:
```bash
mkdir -p ./snapshots
chmod 755 ./snapshots
```

## Next Steps

- Read [README.md](README.md) for detailed architecture information
- Check [CLIENT_EXAMPLE.md](CLIENT_EXAMPLE.md) for client integration examples
- Review [TESTING.md](TESTING.md) for test documentation
- See [BENCHMARK.md](BENCHMARK.md) for performance benchmarking

## Support

For issues or questions:
1. Check the documentation files
2. Review the test cases for usage examples
3. Examine the source code (well-documented)

## License

Educational/demonstration project.
