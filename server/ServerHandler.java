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
            this.inStream = new ObjectInputStream(socket.getInputStream());
            this.outStream = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace(); // Consider using something else (maybe logging instead
        }
    }

    @Override
    public void run() {
        try {
            this.username = (String) inStream.readObject();
            while (true) {
                Message message = (Message) inStream.readObject();
                String messageBody = message.getMessageBody();
                this.username = message.getUser();

                if (messageBody.equalsIgnoreCase("exit")) {
                    pool.removeUser(this);
                    socket.close();
                    break;
                }

                pool.broadcast(message);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Consider better error handling
        }
    }

    public void sendMessageToClients(Message msg) {
        try {
            outStream.writeObject(msg);
        } catch (IOException e) {
            e.printStackTrace(); // Consider using logging instead
        }
    }

    public String getClientName() {
        return this.username;
    }
}
