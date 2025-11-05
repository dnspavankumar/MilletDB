package com.pavan.milletdb.server;

import java.util.Arrays;

/**
 * Represents a parsed command from a client.
 */
public class Command {
    
    private final CommandType type;
    private final String[] args;
    
    public Command(CommandType type, String[] args) {
        this.type = type;
        this.args = args;
    }
    
    public CommandType getType() {
        return type;
    }
    
    public String[] getArgs() {
        return args;
    }
    
    public String getArg(int index) {
        if (index < 0 || index >= args.length) {
            return null;
        }
        return args[index];
    }
    
    public int getArgCount() {
        return args.length;
    }
    
    @Override
    public String toString() {
        return "Command{" +
                "type=" + type +
                ", args=" + Arrays.toString(args) +
                '}';
    }
    
    /**
     * Supported command types.
     */
    public enum CommandType {
        SET,
        GET,
        DEL,
        EXPIRE,
        STATS,
        PING,
        QUIT,
        UNKNOWN
    }
}
