package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;
import shared.Message;

public class ChatClient {
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;
    private Socket socket;

    public void startClient() {
        try {
            socket = new Socket("localhost", 50000);
            outStream = new ObjectOutputStream(socket.getOutputStream());
            inStream = new ObjectInputStream(socket.getInputStream());

            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true);
            listenerThread.start();

            // Read the username and send a registration command.
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();
            Message registerMsg = new Message("REGISTER " + username, username); // CHANGED: Registration command format
            outStream.writeObject(registerMsg);
            outStream.flush();

            System.out.println("Welcome " + username + "! You can start chatting now.");

            // Main loop to send messages.
            while (true) {
                String userInput = scanner.nextLine();
                Message msg = new Message(userInput, username);
                outStream.writeObject(msg);
                outStream.flush(); // NEW: Flush after sending the message

                if (userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("/exit")) {
                    break;
                }
            }

            // NEW: Clean up resources on exit
            scanner.close();
            socket.close();
            inStream.close();
            outStream.close();

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    private void listenToServer() {
        try {
            while (true) {
                Message msg = (Message) inStream.readObject();
                //Display the sender and message.
                if (msg.getUser() == null || msg.getUser().isEmpty()) {
                    System.out.println(msg.getMessageBody());
                } else {
                    System.out.println(msg.getUser() + ": " + msg.getMessageBody());
                }
            }
        } catch (Exception e) {
            System.err.println("Disconnected from server.");
        }
    }


    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.startClient();
    }
}
