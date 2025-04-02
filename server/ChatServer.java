package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class ChatServer {

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(50000)) {  // Port fixed to 50000
            ConnectionPool pool = new ConnectionPool(); // NEW: Using ConnectionPool to track clients
            ChatGroup chatGroup = new ChatGroup();
            System.out.println("Server started on port 50000"); // CHANGED: Added more descriptive logging

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress()); // NEW: Log each connection
                ServerHandler handler = new ServerHandler(socket, pool, chatGroup);
                pool.addClient(handler); // NEW: Register the client in the pool
                new Thread(handler).start(); // CHANGED: Directly starting a new thread for the handler
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start();
    }
}
