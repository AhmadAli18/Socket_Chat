package server;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer{
    private static final int DEFAULT_PORT = 4000;
    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int LOGIN_ATTEMPTS = 3;
    private static final int SOCKET_TIMEOUT = 30000; 
    
    private static final ConcurrentHashMap<String, ClientInfo> clients = new ConcurrentHashMap<>();
    private static final ExecutorService clientThreadPool = Executors.newFixedThreadPool(100);
    public static void main(String[] args){
        int port = DEFAULT_PORT;
        if(args.length > 0){
            try{
                port = Integer.parseInt(args[0]);
                if(port < 1 || port > 65535){
                    throw new NumberFormatException();
                }
            } 
            catch(NumberFormatException e){
                System.out.println("Invalid port. Using default " + DEFAULT_PORT);
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down chat server...");
            gracefulShutdown();
        }));
        try(ServerSocket serverSocket = new ServerSocket(port)){
            serverSocket.setReuseAddress(true);
            System.out.println("✅ Chat server started on port " + port);
            System.out.println("💡 Server will gracefully handle shutdown (Ctrl+C)");     
            while(!Thread.currentThread().isInterrupted()){
                try{
                    Socket clientSocket = serverSocket.accept();
                    clientThreadPool.execute(() -> handleClient(clientSocket));
                } 
                catch(SocketException e){
                    if(!Thread.currentThread().isInterrupted()){
                        System.err.println("Server socket error: " + e.getMessage());
                    }
                    break;
                }
            }
        } catch(IOException e){
            System.err.println("Server error: " + e.getMessage());
        } finally{
            gracefulShutdown();
        }
    }
    private static void gracefulShutdown() {
        System.out.println("Disconnecting all clients...");
        broadcast("INFO Server is shutting down. Goodbye!", null);
        
        // Shutdown thread pool
        clientThreadPool.shutdown();
        try {
            if (!clientThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                clientThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Chat server stopped.");
    }

    private static void handleClient(Socket socket) {
        String username = null;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            socket.setSoTimeout(SOCKET_TIMEOUT);
            
            username = authenticateClient(in, out, socket);
            if(username == null){
                return;
            }
            
            processClientMessages(username, in, out, socket);
            
        } catch(SocketTimeoutException e){
            System.out.println("Client timeout: " + username);
            if(username != null){
                sendToClient(username, "ERR Connection timeout");
            }
        } catch(IOException e){
            System.err.println("Client IO error: " + username + " - " + e.getMessage());
        } finally {
            cleanupClient(username, socket);
        }
    }

    private static String authenticateClient(BufferedReader in, PrintWriter out, Socket socket) throws IOException {
        for(int attempts = 0; attempts < LOGIN_ATTEMPTS; attempts++){
            out.println("Please login using: LOGIN <username>");
            String line = in.readLine();
            if(line == null){
                return null;
            }
            line = line.trim();
            if(line.toUpperCase().startsWith("LOGIN ")){
                String potentialUsername = line.substring(6).trim();
                
                if(!isValidUsername(potentialUsername)){
                    out.println("ERR invalid-username");
                    continue;
                }
                synchronized(clients){
                    if(!clients.containsKey(potentialUsername)){
                        ClientInfo client = new ClientInfo(potentialUsername, out, socket);
                        clients.put(potentialUsername, client);
                        out.println("OK");
                        System.out.println("User logged in: " + potentialUsername + " from " + socket.getInetAddress());
                        broadcast("INFO " + potentialUsername + " joined the chat", potentialUsername);
                        return potentialUsername;
                    } 
                    else{
                        out.println("ERR username-taken");
                    }
                }
            } 
            else{
                out.println("ERR please-login-first");
            }
        } 
        out.println("ERR too-many-attempts");
        return null;
    }

    private static boolean isValidUsername(String username){
        return username != null && 
               !username.isEmpty() && 
               username.length() <= MAX_USERNAME_LENGTH && 
               username.matches("[a-zA-Z0-9_]+") &&
               !username.equalsIgnoreCase("SERVER") &&
               !username.equalsIgnoreCase("INFO") &&
               !username.equalsIgnoreCase("ERR");
    }

    private static void processClientMessages(String username, BufferedReader in, PrintWriter out, Socket socket) throws IOException {
        String msg;
        while((msg = in.readLine()) != null){
            msg = msg.trim();
            if(msg.length() > MAX_MESSAGE_LENGTH){
                out.println("ERR message-too-long");
                continue;
            }
            if(msg.equalsIgnoreCase("QUIT")){
                out.println("INFO Goodbye!");
                break;
            } 
            try{
                if(msg.toUpperCase().startsWith("MSG ")){
                    handleBroadcastMessage(username, msg.substring(4).trim(), out);
                } 
                else if(msg.equalsIgnoreCase("WHO")){
                    handleWhoCommand(out);
                } 
                else if(msg.toUpperCase().startsWith("DM ")){
                    handleDirectMessage(username, msg, out);
                } 
                else if (msg.equalsIgnoreCase("PING")){
                    out.println("PONG");
                } 
                else if (msg.equalsIgnoreCase("HELP")){
                    handleHelpCommand(out);
                } 
                else if (msg.toUpperCase().startsWith("NAME ")){
                    handleNameChange(username, msg.substring(5).trim(), out);
                } 
                else{
                    out.println("ERR unknown-command");
                }
            } catch(Exception e){
                out.println("ERR processing-error");
                System.err.println("Error processing message from " + username + ": " + e.getMessage());
            }
        }
    }

    private static void handleBroadcastMessage(String username, String message, PrintWriter out) {
        if(message.isEmpty()) {
            out.println("ERR empty-message");
            return;
        }
        
        String formattedMessage = "MSG " + username + ": " + message;
        System.out.println("Broadcast from " + username + ": " + message);
        broadcast(formattedMessage, username);
    }

    private static void handleDirectMessage(String username, String message, PrintWriter out) {
        String[] parts = message.split(" ", 3);
        if(parts.length < 3){
            out.println("ERR invalid-dm-format. Use: DM <username> <message>");
            return;
        }
        
        String target = parts[1];
        String text = parts[2].trim();
        
        if (text.isEmpty()) {
            out.println("ERR empty-message");
            return;
        }
        
        if (target.equals(username)) {
            out.println("ERR cannot-dm-yourself");
            return;
        }
        
        ClientInfo targetClient = clients.get(target);
        if (targetClient != null) {
            targetClient.writer.println("DM from " + username + ": " + text);
            out.println("INFO DM sent to " + target);
            System.out.println("DM from " + username + " to " + target + ": " + text);
        } else {
            out.println("ERR user-not-found");
        }
    }

    private static void handleWhoCommand(PrintWriter out) {
        synchronized (clients) {
            if (clients.isEmpty()) {
                out.println("INFO No other users online");
                return;
            }
            
            out.println("INFO Online users (" + clients.size() + "):");
            for (String user : clients.keySet()) {
                ClientInfo client = clients.get(user);
                long minutesOnline = (System.currentTimeMillis() - client.joinTime) / (1000 * 60);
                out.println("USER " + user + " (online for " + minutesOnline + " minutes)");
            }
        }
    }

    private static void handleHelpCommand(PrintWriter out) {
        out.println("INFO Available commands:");
        out.println("INFO   MSG <message>        - Broadcast message to all users");
        out.println("INFO   DM <user> <message>  - Send private message");
        out.println("INFO   WHO                  - List online users");
        out.println("INFO   NAME <new_name>      - Change username");
        out.println("INFO   HELP                 - Show this help");
        out.println("INFO   QUIT                 - Exit chat");
    }

    private static void handleNameChange(String oldUsername, String newUsername, PrintWriter out) {
        if (!isValidUsername(newUsername)) {
            out.println("ERR invalid-username");
            return;
        }
        
        synchronized (clients) {
            if (clients.containsKey(newUsername)) {
                out.println("ERR username-taken");
                return;
            }
            
            ClientInfo client = clients.remove(oldUsername);
            if (client != null) {
                clients.put(newUsername, client);
                client.username = newUsername;
                out.println("OK username-changed");
                broadcast("INFO " + oldUsername + " is now known as " + newUsername, newUsername);
                System.out.println("User renamed: " + oldUsername + " -> " + newUsername);
            }
        }
    }

    private static void broadcast(String message, String sender) {
        List<String> disconnectedUsers = new ArrayList<>();
        
        synchronized (clients) {
            for (ClientInfo client : clients.values()) {
                if (!client.username.equals(sender)) {
                    try {
                        client.writer.println(message);
                        // Check if client is still connected
                        if (client.writer.checkError()) {
                            disconnectedUsers.add(client.username);
                        }
                    } catch (Exception e) {
                        disconnectedUsers.add(client.username);
                    }
                }
            }
        }
        
        // Clean up disconnected clients
        for (String disconnected : disconnectedUsers) {
            cleanupClient(disconnected, null);
        }
    }

    private static void sendToClient(String username, String message) {
        ClientInfo client = clients.get(username);
        if (client != null) {
            try {
                client.writer.println(message);
                if (client.writer.checkError()) {
                    cleanupClient(username, null);
                }
            } catch (Exception e) {
                cleanupClient(username, null);
            }
        }
    }

    private static void cleanupClient(String username, Socket socket) {
        if (username != null) {
            synchronized (clients) {
                ClientInfo removed = clients.remove(username);
                if (removed != null) {
                    broadcast("INFO " + username + " left the chat", username);
                    System.out.println("User disconnected: " + username);
                }
            }
        }
        
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore during cleanup
            }
        }
    }

    private static class ClientInfo {
        String username;
        final PrintWriter writer;
        final long joinTime;
        final Socket socket;
        
        ClientInfo(String username, PrintWriter writer, Socket socket) {
            this.username = username;
            this.writer = writer;
            this.joinTime = System.currentTimeMillis();
            this.socket = socket;
        }
    }
}