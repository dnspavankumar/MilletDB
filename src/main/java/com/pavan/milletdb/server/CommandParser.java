package com.pavan.milletdb.server;

/**
 * Parses text commands into Command objects.
 */
public class CommandParser {
    
    /**
     * Parses a command string into a Command object.
     *
     * @param commandLine the command line to parse
     * @return the parsed Command
     */
    public Command parse(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return new Command(Command.CommandType.UNKNOWN, new String[0]);
        }
        
        String[] parts = commandLine.trim().split("\\s+", 3);
        String cmdStr = parts[0].toUpperCase();
        
        Command.CommandType type;
        try {
            type = Command.CommandType.valueOf(cmdStr);
        } catch (IllegalArgumentException e) {
            type = Command.CommandType.UNKNOWN;
        }
        
        // Extract arguments (everything except the command itself)
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        
        return new Command(type, args);
    }
    
    /**
     * Validates that a command has the required number of arguments.
     *
     * @param command the command to validate
     * @param minArgs minimum number of arguments required
     * @return true if valid, false otherwise
     */
    public boolean validateArgCount(Command command, int minArgs) {
        return command.getArgCount() >= minArgs;
    }
    
    /**
     * Validates that a command has exactly the specified number of arguments.
     *
     * @param command the command to validate
     * @param exactArgs exact number of arguments required
     * @return true if valid, false otherwise
     */
    public boolean validateExactArgCount(Command command, int exactArgs) {
        return command.getArgCount() == exactArgs;
    }
}
