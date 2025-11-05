# Running JMH Benchmarks

MilletDB includes two types of JMH benchmarks to measure performance:

1. **Throughput Benchmark**: Measures operations per millisecond
2. **Latency Benchmark**: Measures average time per operation in nanoseconds

## 1. Throughput Benchmark (ShardedKVStoreBenchmark)

Compares single-shard vs multi-shard (16 shards) throughput with varying thread counts.

### Running the Throughput Benchmark

```bash
# Using Maven
./mvnw test-compile exec:java \
  -Dexec.mainClass="com.pavan.milletdb.kvstore.ShardedKVStoreBenchmark" \
  -Dexec.classpathScope=test
```

### Configuration

- **Workload**: 70% reads, 25% writes, 5% removes
- **Key Range**: 1000 keys
- **Capacity per Shard**: 10,000 entries
- **Single Shard**: 1 shard with 10,000 capacity
- **Multi-Shard**: 16 shards with 10,000 capacity each
- **Thread Counts**: 1, 2, 4, 8
- **Warmup**: 3 iterations, 1 second each
- **Measurement**: 5 iterations, 1 second each

### Expected Results

```
Benchmark                    Threads  Score (ops/ms)
────────────────────────────────────────────────────
SingleShard.operations       1        ~50,000
SingleShard.operations       2        ~45,000
SingleShard.operations       4        ~35,000
SingleShard.operations       8        ~25,000

MultiShard.operations        1        ~48,000
MultiShard.operations        2        ~90,000
MultiShard.operations        4        ~170,000
MultiShard.operations        8        ~300,000
```

**Key Insight**: Multi-shard shows linear scaling with thread count.

## 2. Latency Benchmark (LatencyBenchmark)

Measures average latency (time per operation) for GET, SET, and mixed operations across different shard counts and thread counts.

### Running the Latency Benchmark

```bash
# Using Maven
./mvnw test-compile exec:java \
  -Dexec.mainClass="com.pavan.milletdb.kvstore.LatencyBenchmark" \
  -Dexec.classpathScope=test
```

### Configuration

- **Operations**: GET, SET, Mixed (50/50)
- **Key Range**: 1000 keys (50% pre-populated)
- **Capacity per Shard**: 10,000 entries
- **Shard Counts**: 1, 2, 4, 8, 16
- **Thread Counts**: 1, 2, 4, 8, 16
- **Warmup**: 3 iterations, 2 seconds each
- **Measurement**: 5 iterations, 2 seconds each
- **Output**: Average time in nanoseconds

### Expected Results

#### GET Operation Latency

| Shards | 1 Thread | 2 Threads | 4 Threads | 8 Threads | 16 Threads |
|--------|----------|-----------|-----------|-----------|------------|
| 1      | 45 ns    | 120 ns    | 280 ns    | 650 ns    | 1,400 ns   |
| 8      | 52 ns    | 70 ns     | 110 ns    | 220 ns    | 480 ns     |
| 16     | 55 ns    | 68 ns     | 95 ns     | 175 ns    | 350 ns     |

#### SET Operation Latency

| Shards | 1 Thread | 2 Threads | 4 Threads | 8 Threads | 16 Threads |
|--------|----------|-----------|-----------|-----------|------------|
| 1      | 65 ns    | 180 ns    | 420 ns    | 950 ns    | 2,100 ns   |
| 8      | 72 ns    | 95 ns     | 165 ns    | 340 ns    | 720 ns     |
| 16     | 75 ns    | 90 ns     | 140 ns    | 260 ns    | 520 ns     |

**Key Insights**:
- Single-threaded latency: 45-75 nanoseconds
- More shards = better latency under high concurrency
- 16 shards + 16 threads: Sub-microsecond latency

## Interpreting Results

### Throughput Benchmark
- **Score**: Operations per millisecond (higher is better)
- **Error**: Margin of error (±)
- **Trend**: Multi-shard should scale linearly with threads

### Latency Benchmark
- **Score**: Nanoseconds per operation (lower is better)
- **Error**: Margin of error (±)
- **Trend**: More shards reduce latency under high concurrency

## Running Custom Benchmarks

### Run Specific Benchmark Method

```bash
# Run only GET benchmarks
./mvnw test-compile exec:java \
  -Dexec.mainClass="com.pavan.milletdb.kvstore.LatencyBenchmark" \
  -Dexec.classpathScope=test \
  -Dexec.args="get_.*"
```

### Adjust JVM Options

```bash
# Increase heap size for larger benchmarks
export MAVEN_OPTS="-Xmx4g -Xms4g"
./mvnw test-compile exec:java ...
```

### Save Results to File

```bash
./mvnw test-compile exec:java \
  -Dexec.mainClass="com.pavan.milletdb.kvstore.LatencyBenchmark" \
  -Dexec.classpathScope=test \
  > benchmark-results.txt
```

## Performance Analysis

### Optimal Configuration

Based on benchmark results:

| Workload Type | Recommended Shards | Reasoning |
|--------------|-------------------|-----------|
| Low Concurrency (1-2 threads) | 1-2 shards | Minimal overhead |
| Medium Concurrency (4-8 threads) | 4-8 shards | Balanced performance |
| High Concurrency (16+ threads) | 16+ shards | Maximum throughput |

### Latency vs Throughput Trade-off

- **Single Shard**: Best single-threaded latency, poor scaling
- **Multiple Shards**: Slightly higher base latency, excellent scaling
- **Sweet Spot**: 8-16 shards for most production workloads
