package com.pavan.milletdb.server;

import com.pavan.milletdb.kvstore.ShardedKVStore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * End-to-end load benchmark for NettyServer using real TCP clients.
 *
 * Example:
 * java -cp target/test-classes;target/classes com.pavan.milletdb.server.NettyServerLoadBenchmark 64 15 0.8
 */
public final class NettyServerLoadBenchmark {

    private static final int PORT = 19090;
    private static final int SHARDS = 8;
    private static final int CAPACITY_PER_SHARD = 100_000;
    private static final int SERVER_WORKERS = 32;
    private static final int KEY_SPACE = 50_000;

    private NettyServerLoadBenchmark() {}

    public static void main(String[] args) throws Exception {
        int clients = args.length > 0 ? Integer.parseInt(args[0]) : 64;
        int durationSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 15;
        double getRatio = args.length > 2 ? Double.parseDouble(args[2]) : 0.8d;

        if (clients <= 0 || durationSeconds <= 0 || getRatio < 0.0d || getRatio > 1.0d) {
            throw new IllegalArgumentException("Usage: <clients> <durationSeconds> <getRatio 0..1>");
        }

        ShardedKVStore<String, String> store = new ShardedKVStore<>(SHARDS, CAPACITY_PER_SHARD, 4 * 1024, 1024 * 1024);
        for (int i = 0; i < KEY_SPACE; i++) {
            store.put("k" + i, "v" + i);
        }

        NettyServer server = new NettyServer(store, PORT, SERVER_WORKERS);
        server.start();
        Thread.sleep(500);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong okOps = new AtomicLong();
        AtomicLong errorOps = new AtomicLong();
        AtomicLong totalLatencyNs = new AtomicLong();
        CountDownLatch done = new CountDownLatch(clients);
        List<Thread> workers = new ArrayList<>();

        long startNs = System.nanoTime();
        for (int i = 0; i < clients; i++) {
            Thread t = new Thread(() -> {
                try (Socket socket = new Socket("127.0.0.1", PORT);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(3000);
                    reader.readLine(); // +OK greeting

                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    while (running.get()) {
                        int keyId = random.nextInt(KEY_SPACE);
                        String cmd;
                        if (random.nextDouble() < getRatio) {
                            cmd = "GET k" + keyId + "\r\n";
                        } else {
                            cmd = "SET k" + keyId + " v" + keyId + "\r\n";
                        }

                        long opStart = System.nanoTime();
                        writer.write(cmd);
                        writer.flush();
                        if (readOneResponse(reader)) {
                            okOps.incrementAndGet();
                        } else {
                            errorOps.incrementAndGet();
                        }
                        totalLatencyNs.addAndGet(System.nanoTime() - opStart);
                    }
                } catch (SocketTimeoutException e) {
                    errorOps.incrementAndGet();
                } catch (IOException e) {
                    errorOps.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }, "load-client-" + i);
            t.start();
            workers.add(t);
        }

        Thread.sleep(durationSeconds * 1000L);
        running.set(false);
        done.await(30, TimeUnit.SECONDS);
        long elapsedNs = System.nanoTime() - startNs;

        server.stop();

        double elapsedSec = elapsedNs / 1_000_000_000.0d;
        long success = okOps.get();
        long failed = errorOps.get();
        double rps = success / elapsedSec;
        double avgLatencyMicros = success == 0 ? 0.0d : (totalLatencyNs.get() / (double) success) / 1_000.0d;

        System.out.println("==== MilletDB Netty Load Benchmark ====");
        System.out.println("clients=" + clients + ", durationSec=" + durationSeconds + ", getRatio=" + getRatio);
        System.out.println("successOps=" + success + ", errorOps=" + failed);
        System.out.println(String.format(Locale.US, "throughputRps=%.2f", rps));
        System.out.println(String.format(Locale.US, "avgLatencyMicros=%.2f", avgLatencyMicros));
        System.out.println("=====================================");
    }

    private static boolean readOneResponse(BufferedReader reader) throws IOException {
        String first = reader.readLine();
        if (first == null || first.isEmpty()) {
            return false;
        }
        char type = first.charAt(0);
        if (type == '$') {
            if ("$-1".equals(first)) {
                return true;
            }
            int len = Integer.parseInt(first.substring(1));
            if (len < 0) {
                return false;
            }
            int toRead = len;
            while (toRead > 0) {
                int c = reader.read();
                if (c == -1) {
                    return false;
                }
                toRead--;
            }
            reader.read(); // \r
            reader.read(); // \n
            return true;
        }
        return type == '+' || type == ':';
    }
}

