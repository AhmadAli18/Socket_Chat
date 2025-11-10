# Socket_Chat

JAVA TCP CHAT APPLICATION

This is a simple, multi-threaded TCP chat application written in Java. 
It consists of a server that manages client connections and messages, and a 
client that allows users to connect, chat, and execute various commands.

FEATURES
* Multi-threaded Server: Handles multiple clients concurrently.
* Robust Client: Includes automatic reconnection logic and background active ping.
* Direct Messaging: Send private messages to specific users.
* Broadcast Messaging: Send messages to all connected users.
* User Management: See who is online and change your username.

-----------------------------------------------------------------------------
PREREQUISITES
* Java Development Kit (JDK) 8 or higher.

-----------------------------------------------------------------------------
BUILDING THE APPLICATION

Open your terminal or command prompt in the project root directory.

1. Compile the Server:
   javac server/ChatServer.java

2. Compile the Client:
   javac client/ChatClient.java

-----------------------------------------------------------------------------
RUNNING THE APPLICATION


1. START THE SERVER
   Run the server on a specific port (default is 4000 if not specified).

   Usage:   java server.ChatServer [port]
   Example: java server.ChatServer 5000

2. START A CLIENT
   Run the client by specifying the server's IP address and port.

   Usage:   java client.ChatClient <server_ip> <port>
   Example: java client.ChatClient localhost 5000


AVAILABLE COMMANDS

Once connected, you can use the following commands in the chat client:

LOGIN <username>
  Log in to the server. This must be the first command you send.
  Example: LOGIN Alice

MSG <message>
  Broadcast a message to all currently connected users.
  Example: MSG Hello everyone!

DM <user> <message>
  Send a private, direct message to a specific user.
  Example: DM Bob This is a secret message.

WHO
  List all currently online users and their connection duration.

NAME <new_name>
  Change your current username.
  Example: NAME SuperAlice

PING
  Manually check connection latency to the server.

STATUS
  Display current local and remote connection details.

CLEAR
  Clear the client console screen.

HELP
  Display the list of available commands.

QUIT
  Disconnect from the server and exit the application.
