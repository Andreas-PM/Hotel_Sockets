package server;

// From Practical 4 multiClientChat
public class StartChatServer {
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start();
    }
}
