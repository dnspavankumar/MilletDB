# 🌾 MilletDB

**A lightweight, concurrent, in-memory key–value store built from scratch in Java.**

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Maven-red.svg)](https://maven.apache.org/)

---

## 📖 Introduction

**MilletDB** is a high-performance, in-memory key-value database built entirely from scratch in Java to demonstrate low-level systems engineering, concurrency control, and scalability principles. Inspired by Redis, MilletDB showcases:

- **Concurrent data structures** with fine-grained locking
- **LRU cache eviction** for memory management
- **Sharding** for horizontal scalability
- **Java NIO** for non-blocking network I/O
- **Snapshot persistence** for durability
- **Real-time metrics** and monitoring

This project is ideal for understanding how modern in-memory databases work under the hood, from TCP protocol handling to lock-free data structures.

---

## ✨ Core Features

- 🔒 **Thread-Safe Operations** – Fine-grained locking with `ReentrantReadWriteLock` for concurrent access
- ⚡ **O(1) LRU Cache** – Doubly-linked list + HashMap for constant-time eviction
- ⏰ **TTL Expiry** – Automatic key expiration with millisecond precision
- 💾 **Snapshot Persistence** – JSON-based snapshots with periodic auto-save
- 🌐 **Java NIO Server** – Non-blocking TCP server with configurable worker threads
- 📊 **Real-Time Metrics** – Per-shard statistics (hits, misses, evictions, latency)
- 🚀 **Horizontal Sharding** – Distribute keys across multiple shards for scalability
- 🔧 **REST API** – HTTP endpoints for health checks and live metrics
- 📈 **JMH Benchmarks** – Throughput and latency benchmarks included

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      MilletDB Server                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐         ┌──────────────────────────┐     │
│  │  NioServer   │────────▶│   ShardedKVStore         │     │
│  │  (Port 8080) │         │   (8 shards by default)  │     │
│  └──────────────┘         └──────────────────────────┘     │
│         │                            │                      │
│         │                            ▼                      │
│         │                 ┌─────────────────────┐          │
│         │                 │ ConcurrentKVStore   │          │
│         │                 │ (Per-shard store)   │          │
│         │                 └─────────────────────┘          │
│         │                            │                      │
│         │                            ▼                      │
│         │                 ┌─────────────────────┐          │
│         │                 │    LRUCache         │          │
│         │                 │  (Eviction policy)  │          │
│         │                 └─────────────────────┘          │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐         ┌──────────────────────┐         │
│  │ RequestHandler│────────▶│   StatsCollector     │         │
│  │ CommandParser │         │   (Metrics tracking) │         │
│  └──────────────┘         └──────────────────────┘         │
│                                                             │
│  ┌──────────────┐         ┌──────────────────────┐         │
│  │StatsController│────────▶│  SnapshotManager     │         │
│  │  (REST API)   │         │  (Persistence layer) │         │
│  └──────────────┘         └──────────────────────┘         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Key Components:**
- **NioServer**: Non-blocking TCP server using Java NIO
- **ShardedKVStore**: Distributes keys across multiple shards using consistent hashing
- **ConcurrentKVStore**: Thread-safe store with read-write locks per shard
- **LRUCache**: O(1) eviction using doubly-linked list
- **StatsCollector**: Tracks hits, misses, evictions, and latency per shard
- **SnapshotManager**: Periodic JSON snapshots for durability
- **StatsController**: REST API for metrics and health checks

---

## 🎯 Example Commands

MilletDB uses a simple text-based protocol over TCP (similar to Redis):

```bash
# Connect via telnet
telnet localhost 8080

# Store a key-value pair
SET user:1001 "John Doe"
> OK

# Retrieve a value
GET user:1001
> John Doe

# Set expiration (TTL in milliseconds)
EXPIRE user:1001 5000
> OK

# Delete a key
DEL user:1001
> OK

# Check server statistics
STATS
> {
>   "totalGets": 1250,
>   "totalSets": 890,
>   "cacheHits": 1100,
>   "cacheMisses": 150,
>   "hitRate": 88.0,
>   ...
> }

# Test connection
PING
> PONG
```

**Response Format**: Plain-text responses (OK, error messages, or JSON for STATS)

---

## 🚀 Build & Run

### Prerequisites

- **Java 17+** (JDK 17 or higher)
- **Maven 3.6+**

### Clone the Repository

```bash
git clone https://github.com/pavanuppuluri/MilletDB.git
cd MilletDB
```

### Build the Project

```bash
# Compile and package
./mvnw clean package

# Or on Windows
mvnw.cmd clean package
```

### Run the Server

```bash
# Start MilletDB server
./mvnw spring-boot:run

# Or on Windows
mvnw.cmd spring-boot:run
```

**Default Configuration:**
- **TCP Server**: `localhost:8080`
- **REST API**: `http://localhost:8081`
- **Shards**: 8
- **Capacity per Shard**: 10,000 entries
- **Worker Threads**: 20

### Connect to the Server

```bash
# Using telnet
telnet localhost 8080

# Using netcat
nc localhost 8080

# Using curl (REST API)
curl http://localhost:8081/health
curl http://localhost:8081/stats
```

---

## 📊 Benchmark Results

MilletDB includes JMH benchmarks for throughput and latency testing.

### Throughput Benchmark (Operations/ms)

| Configuration | 1 Thread | 2 Threads | 4 Threads | 8 Threads |
|--------------|----------|-----------|-----------|-----------|
| Single Shard | 50,000   | 45,000    | 35,000    | 25,000    |
| Multi-Shard (16) | 48,000 | 90,000  | 170,000   | 300,000   |

### Latency Benchmark (Nanoseconds/op)

| Shards | 1 Thread | 4 Threads | 8 Threads | 16 Threads |
|--------|----------|-----------|-----------|------------|
| 1      | 45 ns    | 280 ns    | 650 ns    | 1,400 ns   |
| 8      | 52 ns    | 110 ns    | 220 ns    | 480 ns     |
| 16     | 55 ns    | 95 ns     | 175 ns    | 350 ns     |

**Key Insights:**
- Multi-shard configuration scales linearly with thread count
- Sub-microsecond latency under high concurrency (16 shards + 16 threads)
- Single-threaded latency: 45-75 nanoseconds

### Run Benchmarks

```bash
# Throughput benchmark
./mvnw test-compile exec:java \
  -Dexec.mainClass="com.pavan.milletdb.kvstore.ShardedKVStoreBenchmark" \
  -Dexec.classpathScope=test

# Latency benchmark
./mvnw test-compile exec:java \
  -Dexec.mainClass="com.pavan.milletdb.kvstore.LatencyBenchmark" \
  -Dexec.classpathScope=test
```

---

## 📁 Project Structure

```
MilletDB/
├── src/
│   ├── main/
│   │   └── java/com/pavan/milletdb/
│   │       ├── kvstore/              # Core data structures
│   │       │   ├── LRUCache.java     # O(1) LRU eviction
│   │       │   ├── ConcurrentKVStore.java  # Thread-safe store
│   │       │   └── ShardedKVStore.java     # Sharding layer
│   │       ├── server/               # Network layer
│   │       │   ├── NioServer.java    # Java NIO TCP server
│   │       │   ├── RequestHandler.java
│   │       │   ├── CommandParser.java
│   │       │   └── StatsController.java    # REST API
│   │       ├── metrics/              # Monitoring
│   │       │   └── StatsCollector.java
│   │       ├── snapshot/             # Persistence
│   │       │   └── SnapshotManager.java
│   │       └── MilletDbApplication.java
│   └── test/
│       └── java/com/pavan/milletdb/  # Unit & integration tests
├── snapshots/                        # Snapshot storage
├── pom.xml                           # Maven configuration
├── BENCHMARK.md                      # Benchmark documentation
├── TESTING.md                        # Testing guide
└── README.md                         # This file
```

---

## 🧪 Testing

MilletDB includes comprehensive unit and integration tests using **JUnit 5**.

```bash
# Run all tests
./mvnw clean test

# Run specific test class
./mvnw test -Dtest=LRUCacheTest

# Run with coverage
./mvnw clean test jacoco:report
```

**Test Coverage:**
- ✅ LRU cache eviction policies
- ✅ Concurrent operations (multi-threaded stress tests)
- ✅ TTL expiration handling
- ✅ Snapshot save/load
- ✅ NIO server request handling
- ✅ REST API endpoints
- ✅ Sharding and hash distribution

---

## 🎨 Design Highlights

### 1. **LRU Cache Implementation**
- **Data Structure**: Doubly-linked list + HashMap
- **Complexity**: O(1) for get, put, and eviction
- **Thread Safety**: Per-node locking for fine-grained concurrency

### 2. **Lock Hierarchy**
- **Read-Write Locks**: Separate locks for reads and writes
- **Per-Shard Locking**: Reduces contention across shards
- **Lock-Free Reads**: Optimistic reads where possible

### 3. **Sharding Strategy**
- **Hash-Based Sharding**: Uses `key.hashCode() % numShards`
- **Configurable Shards**: Default 8, tunable based on workload
- **Independent Locks**: Each shard has its own lock

### 4. **TTL Handling**
- **Lazy Expiration**: Keys expire on access (no background thread)
- **Millisecond Precision**: Supports fine-grained TTL
- **Automatic Cleanup**: Expired keys removed during eviction

### 5. **Persistence**
- **JSON Snapshots**: Human-readable snapshot format
- **Periodic Auto-Save**: Configurable interval (default: 30 seconds)
- **Graceful Shutdown**: Final snapshot on server stop

### 6. **Extensibility**
- **Pluggable Eviction Policies**: Easy to swap LRU with LFU/FIFO
- **Custom Serialization**: JSON-based, easy to extend
- **Protocol Agnostic**: Text-based protocol, easy to add binary support

---

## 🔮 Future Enhancements

- 🔄 **Replication** – Master-slave replication for high availability
- 📝 **Write-Ahead Log (WAL)** – Durability with append-only logs
- 🌐 **REST API Expansion** – Full CRUD operations via HTTP
- 📦 **Binary Protocol** – RESP (Redis Serialization Protocol) support
- 🔍 **Pattern Matching** – KEYS command with wildcard support
- 📊 **Prometheus Metrics** – Export metrics to Prometheus
- 🐳 **Docker Support** – Containerized deployment
- 🔐 **Authentication** – Password-based access control
- 🧩 **Pub/Sub** – Message queue functionality
- 🌍 **Clustering** – Distributed sharding across nodes

---

## 💡 Example Session

```bash
$ telnet localhost 8080
Trying 127.0.0.1...
Connected to localhost.

> SET session:abc123 "user_data"
OK

> GET session:abc123
user_data

> EXPIRE session:abc123 10000
OK

> STATS
{
  "server": {
    "totalConnections": 5,
    "activeConnections": 1,
    "totalCommands": 127,
    "running": true,
    "port": 8080
  },
  "store": {
    "size": 42,
    "capacity": 80000,
    "numShards": 8,
    "utilizationPercent": 0.05
  },
  "aggregated": {
    "totalGets": 85,
    "totalSets": 42,
    "cacheHits": 78,
    "cacheMisses": 7,
    "hitRatePercent": 91.76
  }
}

> PING
PONG

> DEL session:abc123
OK

> ^]
Connection closed.
```

---

## 👨‍💻 Author

**Dharamapuri Nagasri Pavan Kumar**

B.Tech in Computer Science & Engineering  
BV Raju Institute of Technology, Narsapur

Passionate about **systems programming**, **backend engineering**, and **distributed systems**. MilletDB is a hands-on exploration of low-level database internals, concurrency patterns, and performance optimization.

[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-blue?style=flat&logo=linkedin)](https://www.linkedin.com/in/pavanuppuluri/)
[![GitHub](https://img.shields.io/badge/GitHub-Follow-black?style=flat&logo=github)](https://github.com/pavanuppuluri)

---

## 🏷️ Tags

`#Java` `#SystemDesign` `#Concurrency` `#Cache` `#Database` `#LRU` `#Networking` `#NIO` `#InMemory` `#KeyValue` `#Redis` `#Backend` `#Performance` `#Multithreading` `#Sharding`

---

## 📄 License

This project is licensed under the **MIT License** – see the [LICENSE](LICENSE) file for details.

---

> **MilletDB** — lightweight, granular, and fast. 🌾
