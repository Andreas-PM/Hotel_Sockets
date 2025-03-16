//sourced from Practical 4 - Client Chat Sockets (ChatServerHandler.java)

package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import shared.Message;

public class ServerHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;
    private ConnectionPool pool; // for broadcast message
    private String username;

    public ServerHandler(Socket socket, ConnectionPool pool) {
        this.socket = socket;
        this.pool = pool;

        try {
            this.outStream = new ObjectOutputStream(socket.getOutputStream());
            this.inStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            Message loginMessage = (Message) inStream.readObject();
            this.username = loginMessage.getUser();
            while (true) {
                Message message = (Message) inStream.readObject();
                String messageBody = message.getMessageBody();

                if (messageBody.equalsIgnoreCase("exit")) {
                    pool.removeUser(this);
                    socket.close();
                    break;
                }
                pool.broadcast(message);
            }
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    public void sendMessageToClients(Message msg) {
        try {
            outStream.writeObject(msg);
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    public String getClientName() {
        return this.username;
    }
}
