# MilletDB Benchmarks

This file contains **measured** benchmark output from this repository, not synthetic estimates.

## Benchmark Environment

- Date: `2026-02-22 11:15:59 +05:30`
- OS: `Windows 11 Home Single Language 10.0.26200`
- CPU: `11th Gen Intel Core i5-1135G7` (`4` cores / `8` logical processors)
- RAM: `~19.75 GB` visible
- JVM: `Java 25.0.1 LTS (HotSpot)`

## Commands Used

```powershell
./mvnw clean test-compile
./mvnw dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt
```

Then:

```powershell
$cp = Get-Content -Raw target/runtime-classpath.txt
$fullCp = "$cp;target/classes"

# Throughput (JMH)
java -cp $fullCp org.openjdk.jmh.Main com.pavan.milletdb.kvstore.ShardedKVStoreBenchmark -wi 2 -i 3 -w 1s -r 1s -f 1

# Latency (JMH, focused subset)
java -cp $fullCp org.openjdk.jmh.Main 'com.pavan.milletdb.kvstore.LatencyBenchmark.(get_1Thread|set_1Thread|mixed_1Thread)' -p shardCount=1,8,16 -wi 2 -i 3 -w 1s -r 1s -f 1
```

And for end-to-end TCP:

```powershell
$fullCp = "target/test-classes;target/classes;$cp"
java -cp $fullCp com.pavan.milletdb.server.NettyServerLoadBenchmark 64 20 0.8
java -cp $fullCp com.pavan.milletdb.server.NettyServerLoadBenchmark 32 20 0.8
```

Raw output is stored in:

- `target/benchmarks/throughput-jmh.txt`
- `target/benchmarks/latency-jmh.txt`
- `target/benchmarks/netty-load.txt`

## Throughput Results (JMH, ops/ms)

| Benchmark | Score |
|---|---:|
| `multiShard_1Thread` | `1596.000` |
| `multiShard_2Threads` | `2007.857` |
| `multiShard_4Threads` | `2013.163` |
| `multiShard_8Threads` | `2897.683` |
| `singleShard_1Thread` | `2061.335` |
| `singleShard_2Threads` | `1565.376` |
| `singleShard_4Threads` | `1575.086` |
| `singleShard_8Threads` | `2377.302` |

Notes:

- This run shows better high-thread throughput for `multiShard_*` than `singleShard_*`.
- Confidence intervals are wide in this environment (laptop + background noise). Use longer runs (`-wi/-i/-r`) for publication-grade numbers.

## Latency Results (JMH, ns/op)

| Benchmark | Shards | Score |
|---|---:|---:|
| `get_1Thread` | `1` | `73.414` |
| `get_1Thread` | `8` | `80.611` |
| `get_1Thread` | `16` | `106.992` |
| `mixed_1Thread` | `1` | `564.165` |
| `mixed_1Thread` | `8` | `500.101` |
| `mixed_1Thread` | `16` | `544.452` |
| `set_1Thread` | `1` | `826.617` |
| `set_1Thread` | `8` | `933.540` |
| `set_1Thread` | `16` | `943.561` |

## Netty End-to-End TCP Results

| Clients | Duration | GET Ratio | Throughput (RPS) | Avg Latency (us) | Errors |
|---:|---:|---:|---:|---:|---:|
| `64` | `20s` | `0.8` | `85018.60` | `746.21` | `0` |
| `32` | `20s` | `0.8` | `95183.22` | `333.13` | `0` |

Interpretation:

- On this machine, the server sustained ~`85k` to ~`95k` RPS for this workload.
- Any "100k RPS" claim should be stated conditionally and backed by repeat runs on the target hardware.
