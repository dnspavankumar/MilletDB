package com.pavan.milletdb.server;

import com.pavan.milletdb.kvstore.ShardedKVStore;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles command execution and generates responses.
 * Separated from I/O logic for better testability and maintainability.
 */
public class RequestHandler {
    
    private final ShardedKVStore<String, String> store;
    private final CommandParser parser;
    private final AtomicLong totalCommands;
    private final AtomicLong totalConnections;
    private final AtomicLong activeConnections;
    
    public RequestHandler(ShardedKVStore<String, String> store,
                         AtomicLong totalCommands,
                         AtomicLong totalConnections,
                         AtomicLong activeConnections) {
        this.store = store;
        this.parser = new CommandParser();
        this.totalCommands = totalCommands;
        this.totalConnections = totalConnections;
        this.activeConnections = activeConnections;
    }
    
    /**
     * Processes a command and returns a response.
     *
     * @param commandLine the command line to process
     * @return the response to send to the client
     */
    public Response handleCommand(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return Response.error("Empty command");
        }
        
        Command command = parser.parse(commandLine);
        totalCommands.incrementAndGet();
        
        try {
            switch (command.getType()) {
                case SET:
                    return handleSet(command);
                case GET:
                    return handleGet(command);
                case DEL:
                    return handleDel(command);
                case EXPIRE:
                    return handleExpire(command);
                case STATS:
                    return handleStats();
                case PING:
                    return Response.simpleString("PONG");
                case QUIT:
                    return Response.simpleString("Goodbye");
                case UNKNOWN:
                default:
                    return Response.error("Unknown command '" + commandLine.split("\\s+")[0] + "'");
            }
        } catch (Exception e) {
            return Response.error(e.getMessage());
        }
    }
    
    /**
     * Handles SET key value command.
     */
    private Response handleSet(Command command) {
        if (!parser.validateArgCount(command, 2)) {
            return Response.error("SET requires key and value");
        }
        
        String key = command.getArg(0);
        String value = command.getArg(1);
        
        store.put(key, value);
        return Response.ok();
    }
    
    /**
     * Handles GET key command.
     */
    private Response handleGet(Command command) {
        if (!parser.validateArgCount(command, 1)) {
            return Response.error("GET requires key");
        }
        
        String key = command.getArg(0);
        String value = store.get(key);
        
        return Response.bulkString(value);
    }
    
    /**
     * Handles DEL key command.
     */
    private Response handleDel(Command command) {
        if (!parser.validateArgCount(command, 1)) {
            return Response.error("DEL requires key");
        }
        
        String key = command.getArg(0);
        String removed = store.remove(key);
        
        return Response.integer(removed != null ? 1 : 0);
    }
    
    /**
     * Handles EXPIRE key ttl command.
     */
    private Response handleExpire(Command command) {
        if (!parser.validateArgCount(command, 2)) {
            return Response.error("EXPIRE requires key and ttl");
        }
        
        String key = command.getArg(0);
        long ttl;
        
        try {
            ttl = Long.parseLong(command.getArg(1));
        } catch (NumberFormatException e) {
            return Response.error("Invalid TTL value");
        }
        
        if (ttl <= 0) {
            return Response.error("TTL must be positive");
        }
        
        boolean success = store.expire(key, ttl);
        return Response.integer(success ? 1 : 0);
    }
    
    /**
     * Handles STATS command.
     */
    private Response handleStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("# Server Statistics");
        stats.append("\r\n").append("total_connections:").append(totalConnections.get());
        stats.append("\r\n").append("active_connections:").append(activeConnections.get());
        stats.append("\r\n").append("total_commands:").append(totalCommands.get());
        stats.append("\r\n").append("store_size:").append(store.size());
        stats.append("\r\n").append("store_capacity:").append(store.getTotalCapacity());
        stats.append("\r\n").append("num_shards:").append(store.getNumShards());
        
        return Response.multiLine(stats.toString());
    }
    
    /**
     * Returns the command parser.
     */
    public CommandParser getParser() {
        return parser;
    }
}
