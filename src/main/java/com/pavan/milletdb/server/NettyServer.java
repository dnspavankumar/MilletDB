package com.pavan.milletdb.server;

import com.pavan.milletdb.kvstore.ShardedKVStore;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty-based TCP server for MilletDB command protocol.
 */
public class NettyServer {

    private static final int DEFAULT_WORKER_THREADS = 10;
    private static final int MAX_COMMAND_BYTES = 1024 * 1024;
    private static final String DELIMITER = "\r\n";

    private final ShardedKVStore<String, String> store;
    private final int port;
    private final int workerThreads;

    private final AtomicBoolean running;
    private final AtomicLong totalCommands;
    private final AtomicLong totalConnections;
    private final AtomicLong activeConnections;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;
    private RequestHandler requestHandler;

    public NettyServer(ShardedKVStore<String, String> store, int port) {
        this(store, port, DEFAULT_WORKER_THREADS);
    }

    public NettyServer(ShardedKVStore<String, String> store, int port, int workerThreads) {
        this.store = store;
        this.port = port;
        this.workerThreads = workerThreads;
        this.running = new AtomicBoolean(false);
        this.totalCommands = new AtomicLong(0);
        this.totalConnections = new AtomicLong(0);
        this.activeConnections = new AtomicLong(0);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server is already running");
        }

        requestHandler = new RequestHandler(store, totalCommands, totalConnections, activeConnections);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(workerThreads);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new LineBasedFrameDecoder(MAX_COMMAND_BYTES));
                        p.addLast(new StringDecoder(CharsetUtil.UTF_8));
                        p.addLast(new StringEncoder(CharsetUtil.UTF_8));
                        p.addLast(new CommandChannelHandler(requestHandler, totalConnections, activeConnections));
                    }
                });

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop();
            throw new IllegalStateException("Interrupted while starting Netty server", e);
        } catch (RuntimeException e) {
            stop();
            throw e;
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
        } finally {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().syncUninterruptibly();
                workerGroup = null;
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().syncUninterruptibly();
                bossGroup = null;
            }
            serverChannel = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }

    public ServerStats getStats() {
        return new ServerStats(
            totalConnections.get(),
            activeConnections.get(),
            totalCommands.get(),
            store.size()
        );
    }

    private static final class CommandChannelHandler extends SimpleChannelInboundHandler<String> {

        private final RequestHandler requestHandler;
        private final AtomicLong totalConnections;
        private final AtomicLong activeConnections;

        private CommandChannelHandler(
            RequestHandler requestHandler,
            AtomicLong totalConnections,
            AtomicLong activeConnections
        ) {
            this.requestHandler = requestHandler;
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            totalConnections.incrementAndGet();
            activeConnections.incrementAndGet();
            ctx.writeAndFlush("+OK Connected to MilletDB" + DELIMITER);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            String command = msg == null ? "" : msg.trim();
            if (command.isEmpty()) {
                ctx.writeAndFlush(Response.error("Empty command").serialize());
                return;
            }
            Response response = requestHandler.handleCommand(command);
            ctx.writeAndFlush(response.serialize());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            activeConnections.decrementAndGet();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    public static class ServerStats {
        public final long totalConnections;
        public final long activeConnections;
        public final long totalCommands;
        public final int storeSize;

        public ServerStats(long totalConnections, long activeConnections, long totalCommands, int storeSize) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.totalCommands = totalCommands;
            this.storeSize = storeSize;
        }
    }
}

