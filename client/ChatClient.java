package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import shared.Message;
import shared.SwearFilter;

public class ChatClient {
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;
    private Socket socket;
    private SwearFilter swearFilter = new SwearFilter();
    private AtomicBoolean registrationComplete = new AtomicBoolean(false);

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
            String username = "";
            
            // Loop until successful registration
            while (!registrationComplete.get()) {
                System.out.print("Enter your username: ");
                username = scanner.nextLine();
                
                // Check username for profanity - don't filter, just reject
                if (!swearFilter.isClean(username)) {
                    System.out.println("Username contains inappropriate content. Please choose another username.");
                    continue; // Skip to next iteration without sending to server
                }
                
                Message registerMsg = new Message("REGISTER " + username, username);
                outStream.writeObject(registerMsg);
                outStream.flush();
                
                // Wait for the server's response
                try {
                    // Give time for the server to respond and the listener thread to process
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            System.out.println("You can start chatting now.");

            // Main loop to send messages.
            while (true) {
                System.out.print("> "); // Simple prompt without username
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
                                         12. /name - Show your current username
                                         13. /exit - Exit the chat
                                         """;
                    System.out.println(helpMessage);
                    continue;
                }
                
                // Show the current username
                if (userInputLower.equals("/name")) {
                    System.out.println("Your username: " + username);
                    continue;
                }

                // Check for invalid commands starting with "/"
                if (userInputLower.startsWith("/")) {
                    // List of valid commands
                    String[] validCommands = {
                        "/help", "/exit", "/name", "/topics",
                        "/register ", "/create ", "/join ", "/leave ",
                        "/remove ", "/topic ", "/subscribe ", "/unsubscribe ", "/send "
                    };
                    
                    boolean validCommand = false;
                    for (String cmd : validCommands) {
                        if (cmd.endsWith(" ")) {
                            // For commands that require parameters
                            if (userInputLower.startsWith(cmd)) {
                                validCommand = true;
                                break;
                            }
                        } else {
                            // For commands without parameters
                            if (userInputLower.equals(cmd)) {
                                validCommand = true;
                                break;
                            }
                        }
                    }
                    
                    if (!validCommand) {
                        System.out.println("Invalid command, type /help for assistance");
                        continue;
                    }
                }

                // Intercept /register command to check for profanity in new username
                if (userInputLower.startsWith("/register ")) {
                    Scanner cmdScanner = new Scanner(userInput);
                    cmdScanner.next(); // Skip command
                    if (cmdScanner.hasNext()) {
                        String newUsername = cmdScanner.next();
                        cmdScanner.close();
                        
                        // Check new username for profanity
                        if (!swearFilter.isClean(newUsername)) {
                            System.out.println("Username contains inappropriate content. Please choose another username.");
                            continue; // Skip to next iteration without sending to server
                        }
                    }
                }

                // Apply swear filter to message content
                String filteredUserInput;
                if (userInput.startsWith("/send")) {
                    // For send commands, only filter the message content, not the command parts
                    Scanner scanner1 = new Scanner(userInput);
                    scanner1.next(); // Skip the /send command
                    String target = scanner1.next(); // Skip the target
                    String messageContent = scanner1.hasNextLine() ? scanner1.nextLine().trim() : "";
                    scanner1.close();
                    
                    String filteredContent = swearFilter.filter(messageContent);
                    filteredUserInput = "/send " + target + " " + filteredContent;
                    
                    // Display the filtered message on user's screen
                    System.out.println("[YOU] | " + username + ": /send " + target + " " + filteredContent);
                } else if (!userInput.startsWith("/")) {
                    // Regular message, filter it
                    filteredUserInput = swearFilter.filter(userInput);
                    
                    // Display filtered message on user's screen
                    System.out.println("[YOU] | " + username + ": " + filteredUserInput);
                } else {
                    // Command message, don't filter or display
                    filteredUserInput = userInput;
                }

                // Send the filtered message to the server
                Message msg = new Message(filteredUserInput, username);
                outStream.writeObject(msg);
                outStream.flush();

                if (userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("/exit")) {
                    break;
                }
            }

            // Clean up resources on exit
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
                
                // Check if this is a registration response
                if (msg.getUser().equals("Server")) {
                    // Successful registration check
                    if (msg.getMessageBody().contains("Successfully registered as:")) {
                        registrationComplete.set(true);
                        System.out.println(msg.getMessageBody());
                        continue;
                    }
                    // Username already exists or contains profanity check
                    else if (msg.getMessageBody().contains("already exists") ||
                             msg.getMessageBody().contains("inappropriate content")) {
                        System.out.println(msg.getMessageBody());
                        continue;
                    }
                    // Any other server message - print it
                    System.out.println("Server: " + msg.getMessageBody());
                    continue;
                }
                
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