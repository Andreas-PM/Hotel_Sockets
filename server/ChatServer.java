package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {

    public void start() {
            try {
                ServerSocket server_socket = new ServerSocket(50000);
                ConnectionPool cp = new ConnectionPool();
                System.out.println("Server started");
                while (true) {
                    Socket socket = server_socket.accept();
                    ServerHandler csh = new ServerHandler(socket, cp);
                    cp.addConnects(csh);

                    Thread th = new Thread(csh);
                    th.start();
                }

            } catch (IOException e) {
                    System.err.println("Connection error: " + e.getMessage());
;                }
        }
    public static void main(String[] args) {
    ChatServer server = new ChatServer();
    server.start();
    }
}