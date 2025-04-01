package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;
import shared.Message;

public class ServerHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;
    private ConnectionPool pool;
    private String username = "Anonymous"; // CHANGED: Default to "Anonymous"
    private boolean isRegistered = false;

    public ServerHandler(Socket socket, ConnectionPool pool) {
        this.socket = socket;
        this.pool = pool;
        try {
            // Create output stream first to avoid potential deadlock
            this.outStream = new ObjectOutputStream(socket.getOutputStream());
            this.inStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            System.err.println("Error setting up streams: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            //Process initial registration using a command like "REGISTER myname"
            Message initialMsg = (Message) inStream.readObject();
            String initialBody = initialMsg.getMessageBody();
            Scanner scanner = new Scanner(initialBody);
            if (scanner.hasNext()) {
                String command = scanner.next();
                if (command.equalsIgnoreCase("REGISTER") && scanner.hasNext()) {
                    username = scanner.next();
                    isRegistered = true;
                    System.out.println("User registered: " + username); // NEW: Log registration
                    // NEW: Send confirmation message back to the client
                    Message confirm = new Message("Registration successful as " + username, "Server");
                    sendMessageToClient(confirm);
                } else {
                    // Fallback: use the provided username from the Message object.
                    username = initialMsg.getUser();
                    isRegistered = true;
                    System.out.println("User registered (fallback): " + username);
                }
            }
            scanner.close();

            // Main loop to read further messages from the client.
            while (true) {
                Message msg = (Message) inStream.readObject();
                String body = msg.getMessageBody();

                // Check for exit commands.
                if (body.equalsIgnoreCase("exit") || body.equalsIgnoreCase("/exit")) {
                    pool.removeClient(this); // CHANGED: Use removeClient from ConnectionPool
                    socket.close();
                    System.out.println(username + " disconnected."); // NEW: Log disconnection
                    break;
                }

                // NEW: Process commands – allow re-registration, for example.
                Scanner commandScanner = new Scanner(body);
                if (commandScanner.hasNext()) {
                    String firstWord = commandScanner.next();
                    if (firstWord.equalsIgnoreCase("REGISTER")) {
                        if (commandScanner.hasNext()) {
                            username = commandScanner.next();
                            if (!isRegistered) {
                                pool.addClient(this);
                            }
                            isRegistered = true;
                            System.out.println("User re-registered as: " + username); // NEW: Log re-registration
                            Message confirm = new Message("Successfully registered as: " + username, "Server");
                            sendMessageToClient(confirm);
                        }
                    } else if (firstWord.equalsIgnoreCase("UNREGISTER")) {
                        //UNREGISTER the person, they can use REGISTER to connect again
                        isRegistered = false;
                        pool.removeClient(this);
                        System.out.println("User unregistered: " + username);
                        Message confirm = new Message("You have been unregistered. Register to chat again.", "Server");
                        sendMessageToClient(confirm);
                    } else {
                        //Check user is registered
                        if (!isRegistered) {
                            Message error = new Message("You are not registered. Use 'REGISTER name' to register.", "Server");
                            sendMessageToClient(error);
                        } else {
                            //Show message to clients
                            Message broadcastMsg = new Message(body, username);
                            pool.broadcast(broadcastMsg, this);
                        }
                    }
                }
                commandScanner.close();
            }
        } catch (Exception e) {
            System.err.println("Connection error with user " + username + ": " + e.getMessage());
        }
    }

    public void sendMessageToClient(Message msg) {
        try {
            outStream.writeObject(msg);
            outStream.flush();
        } catch (IOException e) {
            System.err.println("Error sending message to " + username + ": " + e.getMessage());
            pool.removeClient(this); // Remove client to prevent future errors
            // Optionally, close the socket and streams here.
        }
    }

}
