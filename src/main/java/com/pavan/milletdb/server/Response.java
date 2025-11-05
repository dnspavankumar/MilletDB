package com.pavan.milletdb.server;

/**
 * Represents a response to be sent to a client.
 */
public class Response {
    
    private final ResponseType type;
    private final String data;
    
    private static final String DELIMITER = "\r\n";
    
    private Response(ResponseType type, String data) {
        this.type = type;
        this.data = data;
    }
    
    /**
     * Creates a simple string response (e.g., +OK).
     */
    public static Response ok() {
        return new Response(ResponseType.SIMPLE_STRING, "OK");
    }
    
    /**
     * Creates a simple string response with custom message.
     */
    public static Response simpleString(String message) {
        return new Response(ResponseType.SIMPLE_STRING, message);
    }
    
    /**
     * Creates an error response.
     */
    public static Response error(String message) {
        return new Response(ResponseType.ERROR, message);
    }
    
    /**
     * Creates an integer response.
     */
    public static Response integer(long value) {
        return new Response(ResponseType.INTEGER, String.valueOf(value));
    }
    
    /**
     * Creates a bulk string response (with length prefix).
     */
    public static Response bulkString(String value) {
        if (value == null) {
            return new Response(ResponseType.NULL_BULK_STRING, null);
        }
        return new Response(ResponseType.BULK_STRING, value);
    }
    
    /**
     * Creates a multi-line response.
     */
    public static Response multiLine(String content) {
        return new Response(ResponseType.MULTI_LINE, content);
    }
    
    /**
     * Serializes the response to the wire format.
     */
    public String serialize() {
        switch (type) {
            case SIMPLE_STRING:
                return "+" + data + DELIMITER;
            case ERROR:
                return "-ERR " + data + DELIMITER;
            case INTEGER:
                return ":" + data + DELIMITER;
            case BULK_STRING:
                return "$" + data.length() + DELIMITER + data + DELIMITER;
            case NULL_BULK_STRING:
                return "$-1" + DELIMITER;
            case MULTI_LINE:
                return data + DELIMITER;
            default:
                return "-ERR Unknown response type" + DELIMITER;
        }
    }
    
    public ResponseType getType() {
        return type;
    }
    
    public String getData() {
        return data;
    }
    
    @Override
    public String toString() {
        return "Response{" +
                "type=" + type +
                ", data='" + data + '\'' +
                '}';
    }
    
    /**
     * Response types following Redis protocol.
     */
    public enum ResponseType {
        SIMPLE_STRING,    // +OK
        ERROR,            // -ERR message
        INTEGER,          // :123
        BULK_STRING,      // $length\r\ndata
        NULL_BULK_STRING, // $-1
        MULTI_LINE        // Custom multi-line response
    }
}
