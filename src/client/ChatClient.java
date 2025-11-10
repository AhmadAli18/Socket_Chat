package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatClient {
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int RECONNECT_DELAY = 3000;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    
    private static AtomicBoolean connected = new AtomicBoolean(false);
    private static AtomicBoolean reconnecting = new AtomicBoolean(false);

    public static void main(String[] args){
        if(args.length < 2){
            System.out.println("Usage: java client.ChatClient <server_ip> <port>");
            System.out.println("Example: java client.ChatClient localhost 4000");
            return;
        }

        String serverIp = args[0];
        int port;
        
        try{
            port = Integer.parseInt(args[1]);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            System.out.println("Error: Port must be a number between 1 and 65535");
            return;
        }

        connectToServerWithRetry(serverIp, port);
    }

    private static void connectToServerWithRetry(String serverIp, int port) {
        int attempt = 0;
        
        while (attempt < MAX_RECONNECT_ATTEMPTS) {
            try {
                if (attempt > 0) {
                    System.out.println("🔄 Attempting to reconnect... (" + attempt + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                    Thread.sleep(RECONNECT_DELAY);
                }
                
                connectToServer(serverIp, port);
                break;
                
            } catch (ConnectException e) {
                attempt++;
                System.out.println(" Connection failed: " + e.getMessage());
                if (attempt >= MAX_RECONNECT_ATTEMPTS) {
                    System.out.println(" Failed to connect after " + MAX_RECONNECT_ATTEMPTS + " attempts. Giving up.");
                }
            } catch (InterruptedException e) {
                System.out.println(" Reconnection interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                System.out.println("Connection error: " + e.getMessage());
                break;
            }
        }
    }

    private static void connectToServer(String serverIp, int port) throws IOException {
        Socket socket = new Socket();
        try{
            socket.connect(new InetSocketAddress(serverIp, port), CONNECTION_TIMEOUT);
            socket.setSoTimeout(30000); 
            
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 Scanner scanner = new Scanner(System.in)) {
                
                connected.set(true);
                System.out.println("Connected to chat server at " + serverIp + ":" + port);
                System.out.println("Type 'HELP' for available commands");
            
                Thread receiveThread = startMessageReceiver(in, socket);
                // Start ping thread to keep connection alive
                Thread pingThread = startPingService(out);
                
                // Handle user input
                handleUserInput(out, scanner, socket, receiveThread, pingThread);
                
            } catch (SocketTimeoutException e) {
                System.out.println("Connection timeout - server not responding");
            }
            
        } 
        finally{
            connected.set(false);
            if(!socket.isClosed()){
                socket.close();
            }
        }
    }

    private static Thread startMessageReceiver(BufferedReader in, Socket socket) {
        Thread receiveThread = new Thread(() -> {
            try{
                String msg;
                while (connected.get() && (msg = in.readLine()) != null) {
                    displayFormattedMessage(msg);
                    
                    // Check for server shutdown message
                    if (msg.contains("Server is shutting down")) {
                        System.out.println("Server is shutting down. Disconnecting...");
                        connected.set(false);
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                if (connected.get()) {
                    System.out.println("Server timeout - no response received");
                }
            } catch (IOException e) {
                if (connected.get() && !e.getMessage().toLowerCase().contains("socket closed")) {
                    System.out.println("Connection to server lost: " + e.getMessage());
                }
            } finally {
                connected.set(false);
            }
        });
        
        receiveThread.setDaemon(true);
        receiveThread.start();
        return receiveThread;
    }

    private static Thread startPingService(PrintWriter out) {
        Thread pingThread = new Thread(() -> {
            while (connected.get()) {
                try {
                    Thread.sleep(15000); // Ping every 15 seconds
                    if (connected.get()) {
                        out.println("PING");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        pingThread.setDaemon(true);
        pingThread.start();
        return pingThread;
    }

    private static void displayFormattedMessage(String msg) {
        if (msg.startsWith("DM from ")) {
            System.out.println(msg.substring(3));
        } else if (msg.startsWith("INFO ")) {
            String infoMsg = msg.substring(5);
            if (infoMsg.contains("joined") || infoMsg.contains("left") || infoMsg.contains("is now known as")) {
                System.out.println(" " + infoMsg);
            } else {
                System.out.println(" " + infoMsg);
            }
        } else if (msg.startsWith("ERR ")) {
            System.out.println(" " + msg.substring(4));
        } else if (msg.startsWith("MSG ")) {
            System.out.println(" " + msg.substring(4));
        } else if (msg.startsWith("USER ")) {
            System.out.println("    " + msg.substring(5));
        } else if (msg.equals("PONG")) {
            // Silent handling of PONG responses
        } else {
            System.out.println(msg);
        }
    }

    private static void handleUserInput(PrintWriter out, Scanner scanner, Socket socket, 
                                      Thread receiveThread, Thread pingThread) {
        printHelp();
        
        while(connected.get() && scanner.hasNextLine()){
            try{
                String input = scanner.nextLine().trim();
                
                if (input.isEmpty()){
                    continue;
                }
                
                if(input.equalsIgnoreCase("QUIT") || input.equalsIgnoreCase("EXIT")) {
                    handleQuitCommand(out);
                    break;
                }
                
                if(input.equalsIgnoreCase("HELP")){
                    printHelp();
                    continue;
                }
                
                if(input.equalsIgnoreCase("STATUS")){
                    displayConnectionStatus(socket);
                    continue;
                }
                
                if(input.equalsIgnoreCase("CLEAR")) {
                    clearScreen();
                    continue;
                }
                
                // Validate message length before sending
                if (input.length() > 1000) {
                    System.out.println(" Message too long. Maximum 1000 characters allowed.");
                    continue;
                }
        
                out.println(input);
                
                // Check if socket is still connected
                if (socket.isClosed() || !socket.isConnected() || out.checkError()) {
                    System.out.println(" Connection lost while sending message.");
                    connected.set(false);
                    break;
                }
                
            } catch (Exception e) {
                System.out.println(" Input error: " + e.getMessage());
                break;
            }
        }
        
        // Cleanup
        connected.set(false);
        if (pingThread != null && pingThread.isAlive()) {
            pingThread.interrupt();
        }
    }

    private static void handleQuitCommand(PrintWriter out) {
        System.out.println("Disconnecting from chat...");
        out.println("QUIT");
        connected.set(false);
    }

    private static void displayConnectionStatus(Socket socket) {
        if (socket.isConnected() && !socket.isClosed()) {
            System.out.println("Connection status: Connected");
            System.out.println("   Server: " + socket.getInetAddress() + ":" + socket.getPort());
            System.out.println("   Local: " + socket.getLocalAddress() + ":" + socket.getLocalPort());
        } else {
            System.out.println("Connection status: Disconnected");
        }
    }

    private static void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
            System.out.println("Chat Client - Screen cleared");
            System.out.println("Type 'HELP' for available commands");
        } 
        catch(Exception e){
            for(int i = 0; i < 50; i++){
                System.out.println();
            }
            System.out.println("Chat Client");
            System.out.println("Type 'HELP' for available commands");
        }
    }

    private static void printHelp(){
        System.out.println("\n Available Commands:");
        System.out.println("  MSG <message>        - Send message to all users");
        System.out.println("  DM <user> <message>  - Send private message");
        System.out.println("  WHO                  - List online users");
        System.out.println("  NAME <new_name>      - Change your username");
        System.out.println("  PING                 - Check connection");
        System.out.println("  STATUS               - Show connection info");
        System.out.println("  CLEAR                - Clear screen");
        System.out.println("  HELP                 - Show this help");
        System.out.println("  QUIT                 - Exit chat");
        System.out.println();
    }

    private static void attemptReconnection(String serverIp, int port){
        if(reconnecting.compareAndSet(false, true)){
            try{
                System.out.println("Attempting to reconnect...");
                Thread.sleep(RECONNECT_DELAY);
                connectToServer(serverIp, port);
            } 
            catch (Exception e){
                System.out.println("Reconnection failed: " + e.getMessage());
            } 
            finally{
                reconnecting.set(false);
            }
        }
    }
}
