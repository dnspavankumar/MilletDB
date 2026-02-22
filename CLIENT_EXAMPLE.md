# MilletDB Client Examples

## Connecting to MilletDB Server

MilletDB uses a simple text-based protocol over TCP. You can connect using telnet, netcat, or any TCP client.

### Using Telnet

```bash
telnet localhost 6379
```

### Using Netcat

```bash
nc localhost 6379
```

### Using Java Socket

```java
import java.io.*;
import java.net.Socket;

public class MilletDBClient {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 6379);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(
            new InputStreamReader(socket.getInputStream())
        );
        
        // Read welcome message
        System.out.println(in.readLine());
        
        // SET command
        out.println("SET mykey myvalue");
        System.out.println(in.readLine()); // +OK
        
        // GET command
        out.println("GET mykey");
        System.out.println(in.readLine()); // $7 (length)
        System.out.println(in.readLine()); // myvalue
        
        // EXPIRE command (TTL in milliseconds)
        out.println("EXPIRE mykey 5000");
        System.out.println(in.readLine()); // :1
        
        // DEL command
        out.println("DEL mykey");
        System.out.println(in.readLine()); // :1
        
        // STATS command
        out.println("STATS");
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            System.out.println(line);
        }
        
        socket.close();
    }
}
```

## Supported Commands

### SET key value
Stores a key-value pair.

**Request:**
```
SET mykey myvalue
```

**Response:**
```
+OK
```

### GET key
Retrieves the value for a key.

**Request:**
```
GET mykey
```

**Response (if key exists):**
```
$7
myvalue
```

**Response (if key doesn't exist):**
```
$-1
```

### DEL key
Deletes a key.

**Request:**
```
DEL mykey
```

**Response:**
```
:1    (if key was deleted)
:0    (if key didn't exist)
```

### EXPIRE key ttl
Sets a TTL (time-to-live) in milliseconds for a key.

**Request:**
```
EXPIRE mykey 5000
```

**Response:**
```
:1    (if TTL was set)
:0    (if key doesn't exist)
```

### STATS
Returns server statistics.

**Request:**
```
STATS
```

**Response:**
```
# Server Statistics
total_connections:10
active_connections:2
total_commands:50
store_size:25
store_capacity:16000
num_shards:16
```

### PING
Tests server connectivity.

**Request:**
```
PING
```

**Response:**
```
+PONG
```

## Response Format

MilletDB uses a simple response protocol:

- **Simple String**: `+OK` - Starts with `+`
- **Error**: `-ERR message` - Starts with `-`
- **Integer**: `:123` - Starts with `:`
- **Bulk String**: `$length\r\ndata` - Starts with `$`
  - `$-1` indicates null/not found
  - `$0` indicates empty string

## Example Session

```
$ telnet localhost 6379
+OK Connected to MilletDB

SET user:1 John
+OK

SET user:2 Jane
+OK

GET user:1
$4
John

EXPIRE user:1 10000
:1

DEL user:2
:1

STATS
# Server Statistics
total_connections:1
active_connections:1
total_commands:5
store_size:1
store_capacity:16000
num_shards:16

PING
+PONG
```

## Error Handling

If a command is invalid or fails, the server returns an error:

```
GET
-ERR GET requires key

UNKNOWN
-ERR Unknown command 'UNKNOWN'

EXPIRE mykey -100
-ERR TTL must be positive
```

## Connection Management

- Each client connection is handled independently
- The server supports multiple concurrent connections
- Connections are handled by Netty event loops (non-blocking)
- Use `QUIT` command or close the socket to disconnect gracefully
