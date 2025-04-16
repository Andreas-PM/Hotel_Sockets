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
    private SwearFilter swearFilter = new SwearFilter();


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
                String userInputLower = userInput.toLowerCase();

                // Check for "help" without the slash and provide a hint
                if (userInputLower.equals("help")) {
                    System.out.println("Hint: did you mean /help?");
                    continue;
                }

                // Add a help command to display available commands
                if (userInputLower.equals("/help")) {
                    String helpMessage = """
                                         Available commands:
                                         1. /help - Show this help message
                                         2. /register <username> - Register with a username
                                         3. /create <groupName> - Create a new group
                                         4. /join <groupName> - Join an existing group
                                         5. /leave <groupName> - Leave a group
                                         6. /remove <groupName> - Remove a group
                                         7. /topic <topicName> - Create a new topic
                                         8. /subscribe <topicName> - Subscribe to a topic
                                         9. /unsubscribe <topicName> - Unsubscribe from a topic
                                         10. /topics - List all available topics
                                         11. /send <target> <message> - Send a message to a user or group
                                         12. /exit - Exit the chat
                                         """;
                    System.out.println(helpMessage);
                    continue;
                }

                // Check for invalid commands starting with "/"
                if (userInputLower.startsWith("/")) {
                    if (!(userInputLower.equals("/help") ||
                          userInputLower.equals("/exit") ||
                          userInputLower.startsWith("/register ") ||
                          userInputLower.startsWith("/create ") ||
                          userInputLower.startsWith("/join ") ||
                          userInputLower.startsWith("/leave ") ||
                          userInputLower.startsWith("/remove ") ||
                          userInputLower.startsWith("/topic ") ||
                          userInputLower.startsWith("/subscribe ") ||
                          userInputLower.startsWith("/unsubscribe ") ||
                          userInputLower.equals("/topics") ||
                          userInputLower.startsWith("/send "))) {
                        System.out.println("Invalid command, type /help for assistance");
                        continue;
                    }
                }

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