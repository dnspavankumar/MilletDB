# Testing Guide

## Run All Tests

```bash
./mvnw clean test
```

## Run Targeted Classes

```bash
./mvnw test -Dtest=ConcurrentKVStoreTest
./mvnw test -Dtest=SnapshotManagerTest
./mvnw test -Dtest=NettyServerTest
./mvnw test -Dtest=NettyServerIntegrationTest
./mvnw test -Dtest=StatsControllerTest
```

## Important Notes

- Spring tests disable TCP autostart via `src/test/resources/application.properties`:
  - `milletdb.tcp.enabled=false`
- Socket integration tests (`NettyServer*Test`) start their own Netty instance on test ports.
- Snapshot tests use temporary directories (`@TempDir`), so they do not depend on repository snapshot files.

## Coverage Focus

- KV store concurrency and TTL semantics
- Snapshot save/load and expiration behavior
- Netty command protocol over real sockets
- REST health and metrics endpoints
- Command parser and response serialization
