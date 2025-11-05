# MilletDB Testing Guide

## Test Suite Overview

MilletDB has a comprehensive test suite with **133 tests** covering all components from unit tests to integration tests.

## Test Categories

### 1. Unit Tests (81 tests)

#### LRU Cache Tests (14 tests)
- Basic put/get operations
- LRU eviction behavior
- Update and removal operations
- Edge cases (single capacity, null keys/values)

#### Concurrent KV Store Tests (26 tests)
- Thread-safe operations
- TTL and expiration
- Background cleanup thread
- Concurrent read/write operations
- Force cleanup functionality

#### Snapshot Manager Tests (14 tests)
- Save and load snapshots
- Periodic snapshot scheduling
- Multi-shard preservation
- Cleanup of old snapshots
- Expiration handling in snapshots

#### Server Component Tests (27 tests)
- **CommandParser** (15 tests): Command parsing, validation, case-insensitivity
- **RequestHandler** (19 tests): Command execution, error handling, response generation
- **Response** (12 tests): Response serialization, protocol compliance

### 2. Integration Tests (32 tests)

#### NioServer Tests (21 tests)
- Basic server start/stop
- Single client operations (SET, GET, DEL, EXPIRE, STATS, PING)
- Multiple concurrent clients
- Error handling and validation
- Server statistics accuracy

#### NioServer Integration Tests (11 tests)
Comprehensive tests with real socket connections:

1. **testMultipleClientsSequentialOperations**
   - 10 clients performing sequential SET/GET/DEL operations
   - Validates correct isolation between clients

2. **testConcurrentWritesToSameKey**
   - 20 clients writing to the same key concurrently
   - Tests lock contention and data consistency

3. **testConcurrentReadsAndWrites**
   - 30 clients performing 50 operations each
   - Mix of reads and writes to random keys
   - Validates throughput under load

4. **testConcurrentExpireOperations**
   - 15 clients setting TTL on different keys
   - Verifies expiration works correctly under concurrent access

5. **testHighVolumeOperations**
   - 50 clients performing 100 operations each (5000 total)
   - Stress test for server capacity

6. **testClientDisconnectionHandling**
   - 20 clients abruptly disconnecting
   - Ensures server remains stable

7. **testMixedCommandTypes**
   - 25 clients using all command types
   - Tests command routing and processing

8. **testLongRunningConnections**
   - 10 clients maintaining connections for 5 seconds
   - Validates connection stability over time

9. **testStressTestWithRandomOperations**
   - 40 clients performing 50 random operations each
   - Random mix of SET/GET/DEL/EXPIRE/PING
   - Chaos testing for robustness

10. **testServerStatsAccuracy**
    - Validates server statistics are accurate
    - Tests with 15 clients and 10 commands each

11. **testConcurrentStatsRequests**
    - 20 clients requesting STATS simultaneously
    - Tests multi-line response handling

## Running Tests

### Run All Tests
```bash
mvnw test
```

### Run Specific Test Class
```bash
mvnw test -Dtest=NioServerIntegrationTest
mvnw test -Dtest=ConcurrentKVStoreTest
mvnw test -Dtest=SnapshotManagerTest
```

### Run Specific Test Method
```bash
mvnw test -Dtest=NioServerIntegrationTest#testHighVolumeOperations
```

### Run Tests with Verbose Output
```bash
mvnw test -X
```

## Test Coverage

### Component Coverage
- **kvstore package**: 100% (LRUCache, ConcurrentKVStore, ShardedKVStore, Node)
- **server package**: 100% (NioServer, CommandParser, RequestHandler, Command, Response)
- **snapshot package**: 100% (SnapshotManager)

### Scenario Coverage
- ✅ Single-threaded operations
- ✅ Multi-threaded concurrent access
- ✅ High-volume stress testing
- ✅ Network I/O with real sockets
- ✅ Client connection/disconnection
- ✅ TTL and expiration
- ✅ Snapshot persistence
- ✅ Error handling and validation
- ✅ Protocol compliance
- ✅ Server statistics

## Performance Benchmarks

### JMH Benchmarks
Run performance benchmarks comparing single-shard vs multi-shard:

```bash
mvnw test-compile exec:java -Dexec.mainClass="com.pavan.milletdb.kvstore.ShardedKVStoreBenchmark" -Dexec.classpathScope=test
```

See [BENCHMARK.md](BENCHMARK.md) for details.

## Integration Test Characteristics

### Concurrency Levels
- Low: 10 clients
- Medium: 20-30 clients
- High: 40-50 clients

### Operation Volumes
- Light: 10-50 operations per client
- Medium: 50-100 operations per client
- Heavy: 100+ operations per client

### Test Duration
- Quick: < 1 second
- Normal: 1-5 seconds
- Long: 5-10 seconds

### Failure Tolerance
- Most tests expect 100% success rate
- Stress tests allow up to 10% error rate for capacity-related failures

## Continuous Integration

All tests are designed to be:
- **Deterministic**: Same input produces same output
- **Isolated**: Tests don't interfere with each other
- **Fast**: Complete test suite runs in ~15-20 seconds
- **Reliable**: No flaky tests or race conditions

## Test Utilities

### TestClient Helper
Integration tests use a `TestClient` helper class that:
- Manages socket connections
- Handles protocol communication
- Provides blocking and non-blocking read operations
- Auto-closes resources

### Example Usage
```java
try (TestClient client = new TestClient(port)) {
    String response = client.sendCommand("SET key value");
    assertEquals("+OK", response);
    
    String getResponse = client.sendCommand("GET key");
    String value = client.readLine();
    assertEquals("value", value);
}
```

## Known Limitations

1. **Port Conflicts**: Tests use fixed ports (9998, 9999). Ensure these are available.
2. **Timing Sensitivity**: Some tests use `Thread.sleep()` for expiration testing.
3. **Resource Cleanup**: Tests properly clean up resources, but interrupted tests may leave connections open.

## Future Test Enhancements

- [ ] Property-based testing with jqwik
- [ ] Chaos engineering tests (network failures, slow clients)
- [ ] Load testing with JMeter
- [ ] Memory leak detection
- [ ] Performance regression tests
- [ ] Docker-based integration tests
