package com.pavan.milletdb.kvstore;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark measuring GET/SET latency for various shard counts and thread counts.
 * 
 * Measures average latency (time per operation) rather than throughput.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class LatencyBenchmark {
    
    @Param({"1", "2", "4", "8", "16"})
    private int shardCount;
    
    private ShardedKVStore<Integer, String> store;
    private static final int CAPACITY_PER_SHARD = 10000;
    private static final int KEY_RANGE = 1000;
    
    @Setup(Level.Trial)
    public void setup() {
        store = new ShardedKVStore<>(shardCount, CAPACITY_PER_SHARD);
        
        // Pre-populate with 50% of key range
        for (int i = 0; i < KEY_RANGE / 2; i++) {
            store.put(i, "value-" + i);
        }
    }
    
    @Benchmark
    @Threads(1)
    public void get_1Thread() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.get(key);
    }
    
    @Benchmark
    @Threads(2)
    public void get_2Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.get(key);
    }
    
    @Benchmark
    @Threads(4)
    public void get_4Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.get(key);
    }
    
    @Benchmark
    @Threads(8)
    public void get_8Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.get(key);
    }
    
    @Benchmark
    @Threads(16)
    public void get_16Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.get(key);
    }
    
    @Benchmark
    @Threads(1)
    public void set_1Thread() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.put(key, "value-" + key);
    }
    
    @Benchmark
    @Threads(2)
    public void set_2Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.put(key, "value-" + key);
    }
    
    @Benchmark
    @Threads(4)
    public void set_4Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.put(key, "value-" + key);
    }
    
    @Benchmark
    @Threads(8)
    public void set_8Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.put(key, "value-" + key);
    }
    
    @Benchmark
    @Threads(16)
    public void set_16Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        store.put(key, "value-" + key);
    }
    
    @Benchmark
    @Threads(1)
    public void mixed_1Thread() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (ThreadLocalRandom.current().nextBoolean()) {
            store.get(key);
        } else {
            store.put(key, "value-" + key);
        }
    }
    
    @Benchmark
    @Threads(2)
    public void mixed_2Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (ThreadLocalRandom.current().nextBoolean()) {
            store.get(key);
        } else {
            store.put(key, "value-" + key);
        }
    }
    
    @Benchmark
    @Threads(4)
    public void mixed_4Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (ThreadLocalRandom.current().nextBoolean()) {
            store.get(key);
        } else {
            store.put(key, "value-" + key);
        }
    }
    
    @Benchmark
    @Threads(8)
    public void mixed_8Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (ThreadLocalRandom.current().nextBoolean()) {
            store.get(key);
        } else {
            store.put(key, "value-" + key);
        }
    }
    
    @Benchmark
    @Threads(16)
    public void mixed_16Threads() {
        int key = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (ThreadLocalRandom.current().nextBoolean()) {
            store.get(key);
        } else {
            store.put(key, "value-" + key);
        }
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(LatencyBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}
