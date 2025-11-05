package com.pavan.milletdb.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseTest {
    
    @Test
    void testOkResponse() {
        Response response = Response.ok();
        
        assertEquals(Response.ResponseType.SIMPLE_STRING, response.getType());
        assertEquals("OK", response.getData());
        assertEquals("+OK\r\n", response.serialize());
    }
    
    @Test
    void testSimpleStringResponse() {
        Response response = Response.simpleString("PONG");
        
        assertEquals(Response.ResponseType.SIMPLE_STRING, response.getType());
        assertEquals("PONG", response.getData());
        assertEquals("+PONG\r\n", response.serialize());
    }
    
    @Test
    void testErrorResponse() {
        Response response = Response.error("Invalid command");
        
        assertEquals(Response.ResponseType.ERROR, response.getType());
        assertEquals("Invalid command", response.getData());
        assertEquals("-ERR Invalid command\r\n", response.serialize());
    }
    
    @Test
    void testIntegerResponse() {
        Response response = Response.integer(42);
        
        assertEquals(Response.ResponseType.INTEGER, response.getType());
        assertEquals("42", response.getData());
        assertEquals(":42\r\n", response.serialize());
    }
    
    @Test
    void testIntegerZeroResponse() {
        Response response = Response.integer(0);
        
        assertEquals(":0\r\n", response.serialize());
    }
    
    @Test
    void testIntegerNegativeResponse() {
        Response response = Response.integer(-1);
        
        assertEquals(":-1\r\n", response.serialize());
    }
    
    @Test
    void testBulkStringResponse() {
        Response response = Response.bulkString("hello");
        
        assertEquals(Response.ResponseType.BULK_STRING, response.getType());
        assertEquals("hello", response.getData());
        assertEquals("$5\r\nhello\r\n", response.serialize());
    }
    
    @Test
    void testBulkStringEmptyResponse() {
        Response response = Response.bulkString("");
        
        assertEquals("$0\r\n\r\n", response.serialize());
    }
    
    @Test
    void testNullBulkStringResponse() {
        Response response = Response.bulkString(null);
        
        assertEquals(Response.ResponseType.NULL_BULK_STRING, response.getType());
        assertEquals("$-1\r\n", response.serialize());
    }
    
    @Test
    void testMultiLineResponse() {
        String content = "line1\r\nline2\r\nline3";
        Response response = Response.multiLine(content);
        
        assertEquals(Response.ResponseType.MULTI_LINE, response.getType());
        assertEquals(content, response.getData());
        assertEquals(content + "\r\n", response.serialize());
    }
    
    @Test
    void testBulkStringWithSpaces() {
        Response response = Response.bulkString("hello world");
        
        assertEquals("$11\r\nhello world\r\n", response.serialize());
    }
    
    @Test
    void testBulkStringWithSpecialCharacters() {
        Response response = Response.bulkString("hello\nworld");
        
        assertEquals("$11\r\nhello\nworld\r\n", response.serialize());
    }
}
