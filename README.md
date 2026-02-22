# MilletDB

MilletDB is a concurrent in-memory key-value store in Java with:

- sharded storage
- Caffeine-backed bounded cache
- TTL support
- binary snapshots
- Netty TCP server
- REST metrics/health endpoints

## Current Architecture

- `NettyServer`: text command protocol over TCP (`SET`, `GET`, `DEL`, `EXPIRE`, `PING`, `STATS`)
- `ShardedKVStore`: hash-based sharding layer
- `ConcurrentKVStore`: per-shard Caffeine cache + TTL metadata
- `SnapshotManager`: periodic binary snapshots with atomic file replacement
- `StatsController`: `/health` and `/stats`

## What Was Improved

The following reliability/performance issues were addressed:

1. Removed TTL side-map + per-key lock contention in `ConcurrentKVStore`.
2. Unified value + expiration metadata into one record to reduce race windows.
3. Added snapshot coordination gates for consistent capture/restore behavior under concurrent operations.
4. Snapshot files are now written through temp file + atomic move.
5. Migrated naming and usage fully to Netty (`NioServer` alias removed).
6. Enforced entry size limits (`max key bytes`, `max value bytes`) in `ShardedKVStore`.
7. Replaced benchmark docs with measured output and reproducible commands.

## Run

Prerequisites:

- Java 17+ (tested on Java 25)
- Maven 3.9+

Build:

```bash
./mvnw clean package
```

Start:

```bash
./mvnw spring-boot:run
```

Default ports:

- TCP (Netty): `8080`
- HTTP (Spring): `8081`

## Configuration

`src/main/resources/application.properties`:

- `milletdb.tcp.enabled=true`
- `milletdb.tcp.port=8080`
- `milletdb.tcp.workerThreads=20`

For tests, TCP autostart is disabled via `src/test/resources/application.properties`.

## TCP Commands

```text
SET key value
GET key
DEL key
EXPIRE key ttlMillis
PING
STATS
```

## API Endpoints

- `GET /health`
- `GET /stats`

## Benchmarks

Measured benchmark results and exact commands are in `BENCHMARK.md`.

Raw run outputs:

- `target/benchmarks/throughput-jmh.txt`
- `target/benchmarks/latency-jmh.txt`
- `target/benchmarks/netty-load.txt`

## Tests

Run all tests:

```bash
./mvnw clean test
```

## Project Layout

```text
src/main/java/com/pavan/milletdb/
  kvstore/
  server/
  snapshot/
  metrics/
src/test/java/com/pavan/milletdb/
```
