package client;

// Sourced from Practical 4 - Clients chat

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

import shared.Message;

public class ChatClient {
    // Create new object for accessing and reading the input stream
    private ObjectInputStream inStream;

    public void startClient() {
        try {
            Socket socket = new Socket("localhost", 50000);

            ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
            inStream = new ObjectInputStream(socket.getInputStream());

            // Make new thread to keep reading from server (use this for self referencing class) and print out the message
            Thread thread = new Thread(this::listenToServer); //lambda expression
            thread.setDaemon(true); //Daemon Thread = if this is the last thread running, it will self-terminate
            // This is to close the reading from a thread and not continuously read and cause a forever loop
            thread.start();

            // name the user
            Scanner scanner_user = new Scanner(System.in);
            System.out.print("Enter your username: ");
            System.out.flush();
            // This below links to `this.username = (String) inStream.readObject in ChatServerHandler.java`
            String username = scanner_user.nextLine();
            Message loginMessage = new Message("", username); //Empty message string expected from server
            outStream.writeObject(loginMessage);

            System.out.println("Welcome to the world's best chat server " + username + " !");
            System.out.flush();

            // Allow for some input
            Scanner scanner = new Scanner(System.in);
            System.out.println("A client started...");
            System.out.flush();

            // Make it run continuously
            while (true) {
                System.out.print("Enter your message: ");
                System.out.flush();
                String message = scanner.nextLine();

                // This message will be put into the message class
                // This is to make sure there are no errors if duplicate messages are sent from different users
                Message instanceMessage = new Message(message, username);

                // Send the message to the server
                outStream.writeObject(instanceMessage);

                // break case, uses Minecraft-like commands with
                // a leading `/` for telling apart messages and commands
                if (message.equals("/exit")) {
                    socket.close();
                    break;
                }
            }

        } catch (UnknownHostException e) {
            // Make a proper output for the error
            e.printStackTrace();
        } catch (IOException e) {
            // Make a proper output for the error
            e.printStackTrace();
        }
    }

    private void listenToServer() {
        // Then read objects from the server
        try {
            // continuously read from server and print out
            while (true) {
                Message inMessage = (Message) inStream.readObject();
                // Formats the echoed message to be in the format `user: message`
                System.out.println(inMessage.getUser()+":"+inMessage.getMessageBody());
            }
        } catch (Exception e) {
            // TODO: better exception here
            e.printStackTrace();
        }
    }
}
