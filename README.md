# Socket Chat Application

A Java-based multi-threaded socket chat application featuring a robust server-client architecture with support for multiple concurrent users, direct messaging, and real-time communication.

## Features

### Server
- Multi-threaded connection handling (supports up to 100 concurrent clients)
- User authentication with username validation
- Broadcast messaging to all connected users
- Direct messaging (DM) between specific users
- User list and status tracking
- Username change support
- Graceful shutdown handling
- Automatic cleanup of disconnected clients

### Client
- Auto-reconnection on connection loss (up to 3 attempts)
- Keep-alive ping mechanism (every 15 seconds)
- Formatted message display with emoji indicators
- Command-line interface
- Connection status monitoring

## Quick Start

### Running the Server
The server runs automatically on port 3000 when you open this Repl. Check the console to see the server status.

### Running a Client
To connect a client to the server, open the Shell and run:
```bash
./run_client.sh localhost 3000
```

## Available Commands

Once connected, you can use these commands:

- `LOGIN <username>` - Login with a username (alphanumeric and underscore only, max 20 chars)
- `MSG <message>` - Send a message to all connected users
- `DM <user> <message>` - Send a private message to a specific user
- `WHO` - List all currently online users
- `NAME <new_name>` - Change your username
- `PING` - Check if your connection is alive
- `STATUS` - Show connection information (client-side)
- `HELP` - Display available commands
- `QUIT` - Disconnect from the server

## Protocol

The chat uses a simple text-based protocol:
- Server messages start with prefixes: `INFO`, `ERR`, `MSG`, `DM`, `USER`, `OK`, `PONG`
- Clients send commands: `LOGIN`, `MSG`, `DM`, `WHO`, `NAME`, `PING`, `QUIT`, `HELP`

## Building from Source

If you modify the source code, recompile using:

```bash
# Compile server
javac -d build src/server/ChatServer.java

# Compile client
javac -d build src/client/ChatClient.java
```

## Technical Details

- **Language**: Java
- **Default Server Port**: 3000 (configured for Replit)
- **Max Concurrent Clients**: 100
- **Connection Timeout**: 30 seconds
- **Max Username Length**: 20 characters
- **Max Message Length**: 1000 characters
- **Threading**: ExecutorService with fixed thread pool
- **Data Structures**: ConcurrentHashMap for thread-safe client management

## Project Structure

```
Socket_Chat/
├── src/
│   ├── server/
│   │   └── ChatServer.java    # Multi-threaded chat server
│   └── client/
│       └── ChatClient.java    # Chat client
├── build/                      # Compiled .class files
├── run_client.sh              # Helper script to run client
└── README.md
```

## Example Usage

1. Server automatically starts on port 3000
2. Run a client: `./run_client.sh localhost 3000`
3. Login: `LOGIN alice`
4. Send a message: `MSG Hello everyone!`
5. Send a DM: `DM bob Hey Bob, how are you?`
6. List users: `WHO`
7. Quit: `QUIT`
