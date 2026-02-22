# 🌾 MilletDB Usage Guide

A complete guide to interacting with MilletDB — from starting the server to advanced operations.

---

## 📋 Table of Contents

1. [Starting the Server](#starting-the-server)
2. [Connecting to MilletDB](#connecting-to-milletdb)
3. [Basic Commands](#basic-commands)
4. [Advanced Operations](#advanced-operations)
5. [REST API Usage](#rest-api-usage)
6. [Client Examples](#client-examples)
7. [Troubleshooting](#troubleshooting)

---

## 🚀 Starting the Server

### Option 1: Using Maven (Recommended)

```bash
# Start the server
./mvnw spring-boot:run

# On Windows
mvnw.cmd spring-boot:run
```

### Option 2: Using Shell Scripts

```bash
# Linux/Mac
./start-server.sh

# Windows
start-server.cmd
```

### Option 3: Running the JAR

```bash
# Build first
./mvnw clean package

# Run the JAR
java -jar target/MilletDB-0.0.1-SNAPSHOT.jar
```

**Expected Output:**
```
============================================================
  __  __ _ _ _      _   ____  ____  
 |  \/  (_) | | ___| |_|  _ \| __ ) 
 | |\/| | | | |/ _ \ __| | | |  _ \ 
 | |  | | | | |  __/ |_| |_| | |_) |
 |_|  |_|_|_|_|\___|\__|____/|____/ 
                                    
  High-Performance In-Memory Key-Value Store
============================================================
Initializing ShardedKVStore...
  - Number of shards: 8
  - Capacity per shard: 10000
  - Total capacity: 80000
ShardedKVStore initialized successfully
...
Netty Server started successfully
============================================================
MilletDB is ready to accept connections!

Netty Server (TCP):
  - Address: localhost:8080
  - Protocol: Text-based (Redis-like)
  
REST API (HTTP):
  - Address: http://localhost:8081
  - Endpoints:
    GET /stats  - Live metrics in JSON
    GET /health - Health check
============================================================
```

---

## 🔌 Connecting to MilletDB

### Method 1: Using Telnet (Simplest)

```bash
telnet localhost 8080
```

### Method 2: Using Netcat

```bash
nc localhost 8080
```

### Method 3: Using a Custom Client

See [Client Examples](#client-examples) section below.

---

## 📝 Basic Commands

### 1. SET - Store a Key-Value Pair

**Syntax:** `SET <key> <value>`

```bash
SET username "john_doe"
> OK

SET user:1001 "Alice"
> OK

SET counter 42
> OK
```

**Notes:**
- Keys and values are strings
- Use quotes for values with spaces
- Keys are case-sensitive

---

### 2. GET - Retrieve a Value

**Syntax:** `GET <key>`

```bash
GET username
> john_doe

GET user:1001
> Alice

GET counter
> 42

GET nonexistent
> (nil)
```

**Notes:**
- Returns `(nil)` if key doesn't exist
- Returns `(nil)` if key has expired

---

### 3. DEL - Delete a Key

**Syntax:** `DEL <key>`

```bash
DEL username
> OK

DEL nonexistent
> OK
```

**Notes:**
- Always returns `OK` even if key doesn't exist
- Deletion is immediate

---

### 4. EXPIRE - Set Time-To-Live (TTL)

**Syntax:** `EXPIRE <key> <milliseconds>`

```bash
# Set key to expire in 5 seconds (5000ms)
SET session:abc123 "user_data"
> OK

EXPIRE session:abc123 5000
> OK

# Key is still available
GET session:abc123
> user_data

# Wait 5 seconds...
# Key has expired
GET session:abc123
> (nil)
```

**Notes:**
- TTL is in **milliseconds**
- Expiration is lazy (checked on access)
- Setting TTL on non-existent key returns `OK` but has no effect

---

### 5. STATS - View Server Statistics

**Syntax:** `STATS`

```bash
STATS
> {
>   "server": {
>     "totalConnections": 5,
>     "activeConnections": 1,
>     "totalCommands": 127,
>     "running": true,
>     "port": 8080
>   },
>   "store": {
>     "size": 42,
>     "capacity": 80000,
>     "numShards": 8,
>     "capacityPerShard": 10000,
>     "utilizationPercent": 0.05
>   },
>   "shards": {
>     "0": {
>       "size": 5,
>       "capacity": 10000,
>       "gets": 120,
>       "sets": 45,
>       "deletes": 3,
>       "hits": 110,
>       "misses": 10,
>       "hitRate": 91.67,
>       "evictions": 0,
>       "expirations": 2
>     },
>     ...
>   },
>   "aggregated": {
>     "totalGets": 850,
>     "totalSets": 420,
>     "totalDeletes": 15,
>     "totalExpires": 8,
>     "totalOperations": 1293,
>     "cacheHits": 780,
>     "cacheMisses": 70,
>     "hitRatePercent": 91.76,
>     "evictions": 0,
>     "expirations": 12
>   }
> }
```

**Metrics Explained:**
- **totalConnections**: Total connections since server start
- **activeConnections**: Current active connections
- **totalCommands**: Total commands processed
- **size**: Current number of keys in store
- **capacity**: Maximum number of keys
- **utilizationPercent**: Percentage of capacity used
- **hits/misses**: Cache hit/miss counts
- **hitRate**: Cache hit rate percentage
- **evictions**: Number of keys evicted due to capacity
- **expirations**: Number of keys expired due to TTL

---

### 6. PING - Test Connection

**Syntax:** `PING`

```bash
PING
> PONG
```

**Notes:**
- Used to test if server is responsive
- Always returns `PONG`

---

## 🎯 Advanced Operations

### Session Management Example

```bash
# Create a user session
SET session:user123 "{"userId":123,"name":"Alice","role":"admin"}"
> OK

# Set session to expire in 30 minutes (1,800,000 ms)
EXPIRE session:user123 1800000
> OK

# Retrieve session
GET session:user123
> {"userId":123,"name":"Alice","role":"admin"}

# Delete session (logout)
DEL session:user123
> OK
```

---

### Counter Example

```bash
# Initialize counter
SET page:views 0
> OK

# Increment (manual - MilletDB doesn't have INCR yet)
GET page:views
> 0

SET page:views 1
> OK

GET page:views
> 1
```

---

### Caching Example

```bash
# Cache expensive computation result
SET cache:user:123:profile "{"name":"Alice","email":"alice@example.com"}"
> OK

# Set cache to expire in 5 minutes
EXPIRE cache:user:123:profile 300000
> OK

# Retrieve from cache
GET cache:user:123:profile
> {"name":"Alice","email":"alice@example.com"}
```

---

### Namespace Pattern

```bash
# Use colons to create namespaces
SET user:1001:name "Alice"
SET user:1001:email "alice@example.com"
SET user:1002:name "Bob"
SET user:1002:email "bob@example.com"

# Retrieve specific user data
GET user:1001:name
> Alice

GET user:1001:email
> alice@example.com
```

---

## 🌐 REST API Usage

MilletDB also exposes HTTP endpoints for monitoring and health checks.

### Health Check Endpoint

```bash
curl http://localhost:8081/health
```

**Response:**
```json
{
  "status": "UP",
  "nettyServer": "RUNNING",
  "storeSize": 42,
  "timestamp": 1762360583938
}
```

---

### Stats Endpoint

```bash
curl http://localhost:8081/stats
```

**Response:**
```json
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
    "capacityPerShard": 10000,
    "utilizationPercent": 0.05
  },
  "aggregated": {
    "totalGets": 850,
    "totalSets": 420,
    "cacheHits": 780,
    "cacheMisses": 70,
    "hitRatePercent": 91.76
  }
}
```

---

### Using with curl

```bash
# Pretty-print JSON
curl -s http://localhost:8081/stats | jq .

# Check if server is healthy
curl -s http://localhost:8081/health | jq .status

# Monitor stats in real-time (every 2 seconds)
watch -n 2 'curl -s http://localhost:8081/stats | jq .aggregated'
```

---

## 💻 Client Examples

### Java Client

```java
import java.io.*;
import java.net.Socket;

public class MilletDBClient {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
    }
    
    public String set(String key, String value) throws IOException {
        writer.println("SET " + key + " " + value);
        return reader.readLine();
    }
    
    public String get(String key) throws IOException {
        writer.println("GET " + key);
        return reader.readLine();
    }
    
    public String delete(String key) throws IOException {
        writer.println("DEL " + key);
        return reader.readLine();
    }
    
    public void close() throws IOException {
        reader.close();
        writer.close();
        socket.close();
    }
    
    public static void main(String[] args) throws IOException {
        MilletDBClient client = new MilletDBClient();
        client.connect("localhost", 8080);
        
        System.out.println(client.set("username", "alice"));  // OK
        System.out.println(client.get("username"));           // alice
        System.out.println(client.delete("username"));        // OK
        
        client.close();
    }
}
```

---

### Python Client

```python
import socket

class MilletDBClient:
    def __init__(self, host='localhost', port=8080):
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.connect((host, port))
        self.file = self.socket.makefile('rw')
    
    def _send_command(self, command):
        self.file.write(command + '\n')
        self.file.flush()
        return self.file.readline().strip()
    
    def set(self, key, value):
        return self._send_command(f'SET {key} {value}')
    
    def get(self, key):
        return self._send_command(f'GET {key}')
    
    def delete(self, key):
        return self._send_command(f'DEL {key}')
    
    def expire(self, key, milliseconds):
        return self._send_command(f'EXPIRE {key} {milliseconds}')
    
    def ping(self):
        return self._send_command('PING')
    
    def close(self):
        self.file.close()
        self.socket.close()

# Usage
if __name__ == '__main__':
    client = MilletDBClient()
    
    print(client.set('username', 'alice'))  # OK
    print(client.get('username'))           # alice
    print(client.ping())                    # PONG
    print(client.delete('username'))        # OK
    
    client.close()
```

---

### Node.js Client

```javascript
const net = require('net');

class MilletDBClient {
    constructor(host = 'localhost', port = 8080) {
        this.client = new net.Socket();
        this.client.connect(port, host);
    }
    
    sendCommand(command) {
        return new Promise((resolve, reject) => {
            this.client.write(command + '\n');
            this.client.once('data', (data) => {
                resolve(data.toString().trim());
            });
            this.client.once('error', reject);
        });
    }
    
    async set(key, value) {
        return await this.sendCommand(`SET ${key} ${value}`);
    }
    
    async get(key) {
        return await this.sendCommand(`GET ${key}`);
    }
    
    async delete(key) {
        return await this.sendCommand(`DEL ${key}`);
    }
    
    async expire(key, milliseconds) {
        return await this.sendCommand(`EXPIRE ${key} ${milliseconds}`);
    }
    
    async ping() {
        return await this.sendCommand('PING');
    }
    
    close() {
        this.client.destroy();
    }
}

// Usage
(async () => {
    const client = new MilletDBClient();
    
    console.log(await client.set('username', 'alice'));  // OK
    console.log(await client.get('username'));           // alice
    console.log(await client.ping());                    // PONG
    console.log(await client.delete('username'));        // OK
    
    client.close();
})();
```

---

### Bash Script

```bash
#!/bin/bash

# Simple MilletDB client using netcat

HOST="localhost"
PORT=8080

# Function to send command
send_command() {
    echo "$1" | nc $HOST $PORT
}

# Examples
send_command "SET mykey myvalue"
send_command "GET mykey"
send_command "EXPIRE mykey 5000"
send_command "DEL mykey"
send_command "PING"
```

---

## 🔧 Troubleshooting

### Server Won't Start

**Problem:** Port 8080 or 8081 already in use

**Solution:**
```bash
# Check what's using the port
netstat -ano | findstr :8080  # Windows
lsof -i :8080                 # Linux/Mac

# Kill the process or change MilletDB port in application.properties
```

---

### Can't Connect via Telnet

**Problem:** Connection refused

**Solution:**
1. Verify server is running: `curl http://localhost:8081/health`
2. Check firewall settings
3. Ensure you're connecting to correct port (8080 for TCP, 8081 for HTTP)

---

### Commands Not Working

**Problem:** Getting error responses

**Common Issues:**
- **Syntax Error**: Check command format (e.g., `SET key value`, not `SET key=value`)
- **Missing Quotes**: Use quotes for values with spaces: `SET name "John Doe"`
- **Case Sensitivity**: Keys are case-sensitive: `mykey` ≠ `MyKey`

---

### Keys Disappearing

**Problem:** Keys vanish unexpectedly

**Possible Causes:**
1. **TTL Expiration**: Check if you set an EXPIRE on the key
2. **Eviction**: Store reached capacity, oldest keys evicted
3. **Server Restart**: Data is in-memory, lost on restart (unless snapshot loaded)

**Solution:**
- Check STATS to see eviction/expiration counts
- Increase capacity in configuration
- Ensure snapshots are enabled for persistence

---

### Slow Performance

**Problem:** Commands taking too long

**Solutions:**
1. **Increase Shards**: More shards = better concurrency
2. **Check Capacity**: If store is full, evictions slow down operations
3. **Monitor Stats**: Use `/stats` endpoint to check hit rates
4. **Reduce Snapshot Frequency**: Frequent snapshots can impact performance

---

## 📚 Additional Resources

- **[README.md](README.md)** - Project overview and architecture
- **[BENCHMARK.md](BENCHMARK.md)** - Performance benchmarks
- **[TESTING.md](TESTING.md)** - Testing guide
- **[CLIENT_EXAMPLE.md](CLIENT_EXAMPLE.md)** - More client examples

---

## 🎓 Quick Reference Card

```
┌─────────────────────────────────────────────────────────┐
│                  MilletDB Quick Reference               │
├─────────────────────────────────────────────────────────┤
│ SET key value          Store a key-value pair           │
│ GET key                Retrieve a value                 │
│ DEL key                Delete a key                     │
│ EXPIRE key ms          Set TTL in milliseconds          │
│ STATS                  Show server statistics           │
│ PING                   Test connection                  │
├─────────────────────────────────────────────────────────┤
│ TCP Server:  localhost:8080                             │
│ REST API:    http://localhost:8081                      │
│              GET /health                                │
│              GET /stats                                 │
└─────────────────────────────────────────────────────────┘
```

---

> **Happy Caching!** 🌾 For questions or issues, open an issue on [GitHub](https://github.com/dnspavankumar/MilletDB).

