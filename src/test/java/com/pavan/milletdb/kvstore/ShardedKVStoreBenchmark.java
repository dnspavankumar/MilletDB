package com.pavan.milletdb.kvstore;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark comparing single-shard vs multi-shard KV store performance.
 * Tests with 1, 2, 4, and 8 threads to measure scalability.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ShardedKVStoreBenchmark {
    
    @Param({"1", "2", "4", "8"})
    private int threads;
    
    private ShardedKVStore<Integer, String> singleShard;
    private ShardedKVStore<Integer, String> multiShard;
    
    private static final int CAPACITY_PER_SHARD = 10000;
    private static final int KEY_RANGE = 1000;
    
    @Setup(Level.Trial)
    public void setup() {
        // Single shard (1 shard)
        singleShard = new ShardedKVStore<>(1, CAPACITY_PER_SHARD);
        
        // Multi-shard (16 shards)
        multiShard = new ShardedKVStore<>(16, CAPACITY_PER_SHARD);
        
        // Pre-populate both stores
        for (int i = 0; i < KEY_RANGE / 2; i++) {
            singleShard.put(i, "value-" + i);
            multiShard.put(i, "value-" + i);
        }
    }
    
    @Benchmark
    @Threads(1)
    public void singleShard_1Thread(SingleShardState state) {
        performOperations(state.singleShard);
    }
    
    @Benchmark
    @Threads(2)
    public void singleShard_2Threads(SingleShardState state) {
        performOperations(state.singleShard);
    }
    
    @Benchmark
    @Threads(4)
    public void singleShard_4Threads(SingleShardState state) {
        performOperations(state.singleShard);
    }
    
    @Benchmark
    @Threads(8)
    public void singleShard_8Threads(SingleShardState state) {
        performOperations(state.singleShard);
    }
    
    @Benchmark
    @Threads(1)
    public void multiShard_1Thread(MultiShardState state) {
        performOperations(state.multiShard);
    }
    
    @Benchmark
    @Threads(2)
    public void multiShard_2Threads(MultiShardState state) {
        performOperations(state.multiShard);
    }
    
    @Benchmark
    @Threads(4)
    public void multiShard_4Threads(MultiShardState state) {
        performOperations(state.multiShard);
    }
    
    @Benchmark
    @Threads(8)
    public void multiShard_8Threads(MultiShardState state) {
        performOperations(state.multiShard);
    }
    
    private void performOperations(ShardedKVStore<Integer, String> store) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int key = random.nextInt(KEY_RANGE);
        int operation = random.nextInt(100);
        
        if (operation < 70) {
            // 70% reads
            store.get(key);
        } else if (operation < 95) {
            // 25% writes
            store.put(key, "value-" + key);
        } else {
            // 5% removes
            store.remove(key);
        }
    }
    
    @State(Scope.Benchmark)
    public static class SingleShardState {
        ShardedKVStore<Integer, String> singleShard;
        
        @Setup(Level.Trial)
        public void setup() {
            singleShard = new ShardedKVStore<>(1, CAPACITY_PER_SHARD);
            for (int i = 0; i < KEY_RANGE / 2; i++) {
                singleShard.put(i, "value-" + i);
            }
        }
    }
    
    @State(Scope.Benchmark)
    public static class MultiShardState {
        ShardedKVStore<Integer, String> multiShard;
        
        @Setup(Level.Trial)
        public void setup() {
            multiShard = new ShardedKVStore<>(16, CAPACITY_PER_SHARD);
            for (int i = 0; i < KEY_RANGE / 2; i++) {
                multiShard.put(i, "value-" + i);
            }
        }
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ShardedKVStoreBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}
