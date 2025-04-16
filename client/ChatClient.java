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
    private final SwearFilter swearFilter = new SwearFilter();
    private final AtomicBoolean registrationComplete = new AtomicBoolean(false);
    private final Object registrationLock = new Object(); // Add lock object for synchronization

    public void startClient() {
        try {
            socket = new Socket("localhost", 50000);
            outStream = new ObjectOutputStream(socket.getOutputStream());
            inStream = new ObjectInputStream(socket.getInputStream());

            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true);
            listenerThread.start();

            try ( // Read the username and send a registration command.
                    Scanner scanner = new Scanner(System.in)) {
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
                    
                    // Wait for the server's response using proper synchronization
                    synchronized (registrationLock) {
                        try {
                            // Wait for notification from the listener thread
                            registrationLock.wait(5000); // Add timeout for safety
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    
                    // If we're still not registered after waiting, server might be unresponsive
                    if (!registrationComplete.get()) {
                        System.out.println("Registration attempt timed out. Please try again.");
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
                    if (userInputLower.startsWith("/help")) {
                        String[] parts = userInputLower.split("\\s+", 2);
                        String helpFlag = parts.length > 1 ? parts[1].toLowerCase() : "";
                        
                        // Define all help messages in variables for reuse
                        String basicHelp = """
                                                               Basic Commands:
                                                               1. /register <username> - Register with a username
                                                               2. /name - Show your current username
                                                               3. /exit - Exit the chat
                                                               """;
                        
                        String groupHelp = """
                                                               Group Commands:
                                                               1. /group <flag> <groupName>
                                                                 1. create - Create a new group
                                                                 2. join - Join an existing group
                                                                 3. leave - Leave a group
                                                                 4. remove - Remove a group
                                                                 5. list - Show all available groups
                                                               """;
                        
                        String topicHelp = """
                                                               Topic Commands:
                                                               1. /topic <flag> <topicName>
                                                                 1. create - Create a new topic
                                                                 2. subscribe - Subscribe to a topic
                                                                 3. unsubscribe - Unsubscribe from a topic
                                                                 4. list - List all available topics
                                                               """;
                        
                        String userHelp = """
                                                              User Commands:
                                                              1. /user <flag>
                                                                1. list - Show a list of all online users
                                                                2. count - Show the number of users online
                                                              """;
                        
                        String messageHelp = """
                                                                 Message Commands:
                                                                 1. /send <target> <message> - Send a message to a user or group (legacy format)
                                                                 2. /send user <username> <message> - Send a direct message to a specific user
                                                                 3. /send group <groupname> <message> - Send a message to a specific group
                                                                 """;
                        
                        if (helpFlag.isEmpty()) {
                            // Show general help overview
                            String helpMessage = """
                                                                         Available command categories:
                                                                         1. /help - Show this help overview
                                                                         2. /help basic - Basic commands (register, name, exit)
                                                                         3. /help group - Group-related commands
                                                                         4. /help topic - Topic-related commands
                                                                         5. /help user - User-related commands
                                                                         6. /help message - Message-related commands
                                                                         7. /help all - Show all commands
                                                                         """;
                            System.out.println(helpMessage);
                        } else if (helpFlag.equals("basic")) {
                            System.out.println(basicHelp);
                        } else if (helpFlag.equals("group")) {
                            System.out.println(groupHelp);
                        } else if (helpFlag.equals("topic")) {
                            System.out.println(topicHelp);
                        } else if (helpFlag.equals("user")) {
                            System.out.println(userHelp);
                        } else if (helpFlag.equals("message")) {
                            System.out.println(messageHelp);
                        } else if (helpFlag.equals("all")) {
                            System.out.println("All Available Commands:\n");
                            System.out.println(basicHelp);
                            System.out.println(groupHelp);
                            System.out.println(topicHelp);
                            System.out.println(userHelp);
                            System.out.println(messageHelp);
                        } else {
                            System.out.println("Unknown help category: " + helpFlag);
                            System.out.println("Available categories: basic, group, topic, user, message, all");
                        }
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
                            "/remove ", "/topic ", "/subscribe ", "/unsubscribe ", "/send ",
                            "/user ", "/group "
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
                    
                    // Intercept /send command to apply filtering based on new format
                    String filteredUserInput;
                    if (userInputLower.startsWith("/send")) {
                        try (Scanner cmdScanner = new Scanner(userInput)) {
                            cmdScanner.next(); // Skip the /send command
                            
                            if (cmdScanner.hasNext()) {
                                String targetTypeOrTarget = cmdScanner.next();
                                
                                // Check if it's the new format (/send user|group <target>) or legacy format (/send <target>)
                                if (targetTypeOrTarget.equalsIgnoreCase("user") || targetTypeOrTarget.equalsIgnoreCase("group")) {
                                    // New format
                                    if (cmdScanner.hasNext()) {
                                        String target = cmdScanner.next();
                                        String messageContent = cmdScanner.hasNextLine() ? cmdScanner.nextLine().trim() : "";
                                        
                                        String filteredContent = swearFilter.filter(messageContent);
                                        filteredUserInput = "/send " + targetTypeOrTarget + " " + target + " " + filteredContent;
                                        
                                        // Display the filtered message on user's screen
                                        System.out.println("[YOU] | " + username + ": /send " + targetTypeOrTarget + " " +
                                                target + " " + filteredContent);
                                    } else {
                                        // Missing target
                                        filteredUserInput = userInput;
                                    }
                                } else {
                                    // Legacy format - target is already in targetTypeOrTarget
                                    String messageContent = cmdScanner.hasNextLine() ? cmdScanner.nextLine().trim() : "";
                                    String filteredContent = swearFilter.filter(messageContent);
                                    filteredUserInput = "/send " + targetTypeOrTarget + " " + filteredContent;
                                    
                                    // Display the filtered message on user's screen
                                    System.out.println("[YOU] | " + username + ": /send " + targetTypeOrTarget + " " + filteredContent);
                                }
                            } else {
                                // Missing target type or target
                                filteredUserInput = userInput;
                            }
                        } // Skip the /send command
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
            }
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
                        synchronized (registrationLock) {
                            registrationLock.notifyAll(); // Notify waiting thread
                        }
                        System.out.println(msg.getMessageBody());
                        continue;
                    }
                    // Username already exists or contains profanity check
                    else if (msg.getMessageBody().contains("already exists") ||
                             msg.getMessageBody().contains("inappropriate content")) {
                        synchronized (registrationLock) {
                            registrationLock.notifyAll(); // Notify waiting thread
                        }
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
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Disconnected from server.");
        }
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.startClient();
    }
}