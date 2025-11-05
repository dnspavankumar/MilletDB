package com.pavan.milletdb.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {
    
    private CommandParser parser;
    
    @BeforeEach
    void setUp() {
        parser = new CommandParser();
    }
    
    @Test
    void testParseSetCommand() {
        Command cmd = parser.parse("SET key value");
        
        assertEquals(Command.CommandType.SET, cmd.getType());
        assertEquals(2, cmd.getArgCount());
        assertEquals("key", cmd.getArg(0));
        assertEquals("value", cmd.getArg(1));
    }
    
    @Test
    void testParseGetCommand() {
        Command cmd = parser.parse("GET mykey");
        
        assertEquals(Command.CommandType.GET, cmd.getType());
        assertEquals(1, cmd.getArgCount());
        assertEquals("mykey", cmd.getArg(0));
    }
    
    @Test
    void testParseDelCommand() {
        Command cmd = parser.parse("DEL key1");
        
        assertEquals(Command.CommandType.DEL, cmd.getType());
        assertEquals(1, cmd.getArgCount());
        assertEquals("key1", cmd.getArg(0));
    }
    
    @Test
    void testParseExpireCommand() {
        Command cmd = parser.parse("EXPIRE key1 5000");
        
        assertEquals(Command.CommandType.EXPIRE, cmd.getType());
        assertEquals(2, cmd.getArgCount());
        assertEquals("key1", cmd.getArg(0));
        assertEquals("5000", cmd.getArg(1));
    }
    
    @Test
    void testParseStatsCommand() {
        Command cmd = parser.parse("STATS");
        
        assertEquals(Command.CommandType.STATS, cmd.getType());
        assertEquals(0, cmd.getArgCount());
    }
    
    @Test
    void testParsePingCommand() {
        Command cmd = parser.parse("PING");
        
        assertEquals(Command.CommandType.PING, cmd.getType());
        assertEquals(0, cmd.getArgCount());
    }
    
    @Test
    void testParseCaseInsensitive() {
        Command cmd1 = parser.parse("set key value");
        Command cmd2 = parser.parse("SET key value");
        Command cmd3 = parser.parse("SeT key value");
        
        assertEquals(Command.CommandType.SET, cmd1.getType());
        assertEquals(Command.CommandType.SET, cmd2.getType());
        assertEquals(Command.CommandType.SET, cmd3.getType());
    }
    
    @Test
    void testParseWithMultipleSpaces() {
        Command cmd = parser.parse("SET   key   value");
        
        assertEquals(Command.CommandType.SET, cmd.getType());
        assertEquals(2, cmd.getArgCount());
        assertEquals("key", cmd.getArg(0));
        assertEquals("value", cmd.getArg(1));
    }
    
    @Test
    void testParseValueWithSpaces() {
        Command cmd = parser.parse("SET mykey hello world");
        
        assertEquals(Command.CommandType.SET, cmd.getType());
        assertEquals(2, cmd.getArgCount());
        assertEquals("mykey", cmd.getArg(0));
        assertEquals("hello world", cmd.getArg(1));
    }
    
    @Test
    void testParseEmptyCommand() {
        Command cmd = parser.parse("");
        
        assertEquals(Command.CommandType.UNKNOWN, cmd.getType());
        assertEquals(0, cmd.getArgCount());
    }
    
    @Test
    void testParseNullCommand() {
        Command cmd = parser.parse(null);
        
        assertEquals(Command.CommandType.UNKNOWN, cmd.getType());
        assertEquals(0, cmd.getArgCount());
    }
    
    @Test
    void testParseUnknownCommand() {
        Command cmd = parser.parse("UNKNOWN arg1 arg2");
        
        assertEquals(Command.CommandType.UNKNOWN, cmd.getType());
        assertEquals(2, cmd.getArgCount());
    }
    
    @Test
    void testValidateArgCount() {
        Command cmd = parser.parse("SET key value");
        
        assertTrue(parser.validateArgCount(cmd, 2));
        assertTrue(parser.validateArgCount(cmd, 1));
        assertFalse(parser.validateArgCount(cmd, 3));
    }
    
    @Test
    void testValidateExactArgCount() {
        Command cmd = parser.parse("GET key");
        
        assertTrue(parser.validateExactArgCount(cmd, 1));
        assertFalse(parser.validateExactArgCount(cmd, 0));
        assertFalse(parser.validateExactArgCount(cmd, 2));
    }
    
    @Test
    void testGetArgOutOfBounds() {
        Command cmd = parser.parse("GET key");
        
        assertEquals("key", cmd.getArg(0));
        assertNull(cmd.getArg(1));
        assertNull(cmd.getArg(-1));
    }
}
