# Project Summary

## What MilletDB Is

MilletDB is a Java in-memory key-value store with:

- sharded storage (`ShardedKVStore`)
- Caffeine-backed bounded cache
- TTL support
- binary snapshot persistence
- Netty TCP server
- Spring REST stats/health endpoints

## Key Technical Decisions

1. `NettyServer` is the only TCP server implementation (legacy `NioServer` alias removed).
2. `ConcurrentKVStore` stores value + TTL metadata together, avoiding the prior side-map lock contention.
3. Snapshot capture/restore is coordinated through store-level gates to improve consistency under concurrent traffic.
4. Snapshot file writes are atomic (temp file + move).
5. Entry size limits are enforced at store write time.

## Benchmarks

Measured benchmarks were executed and documented in `BENCHMARK.md`, with raw outputs saved under `target/benchmarks/`.

## Testing

Run:

```bash
./mvnw clean test
```

Detailed testing commands: `TESTING.md`.
