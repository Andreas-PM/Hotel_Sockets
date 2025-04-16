package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;
import shared.Message;
import shared.SwearFilter;

public class ServerHandler implements Runnable {
    private final Socket socket;
    private final ObjectInputStream inStream;
    private final ObjectOutputStream outStream;
    private final ConnectionPool pool;
    private final ChatGroup chatGroup;
    private final TopicHandler topicHandler;
    private String username = "Anonymous";
    private String currentGroup = "";
    private boolean isRegistered = false;
    private final SwearFilter swearFilter = new SwearFilter(); // Using shared SwearFilter

    public ServerHandler(Socket socket, ConnectionPool pool, ChatGroup chatGroup, TopicHandler topicHandler) {
        this.socket = socket;
        this.pool = pool;
        this.chatGroup = chatGroup;
        this.topicHandler = topicHandler;
        try {
            // Create output stream first to avoid potential deadlock
            this.outStream = new ObjectOutputStream(socket.getOutputStream());
            this.inStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            System.err.println("Error setting up streams: " + e.getMessage());
            throw new RuntimeException("Failed to initialize streams", e);
        }
    }

    public String getUsername() {
        return username;
    }

    public String getCurrentGroup() { return currentGroup; }
    
    public void setCurrentGroup(String group) { 
        this.currentGroup = group; 
    }

    @Override
    public void run() {
        try {
            // Process user registration until successful
            processInitialRegistration();
            
            // Main communication loop
            handleClientCommunication();
            
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Connection error with user " + username + ": " + e.getMessage());
            if (isRegistered) {
                pool.removeClient(this);
            }
        }
    }
    
    private void processInitialRegistration() throws IOException, ClassNotFoundException {
        boolean registrationSuccessful = false;
        
        while (!registrationSuccessful) {
            Message initialMsg = (Message) inStream.readObject();
            String initialBody = initialMsg.getMessageBody();
            
            // Check if this is a REGISTER command
            if (initialBody.startsWith("REGISTER ")) {
                // Extract username from the REGISTER command, preserving spaces
                String requestedUsername = initialBody.substring("REGISTER ".length()).trim();
                
                // If username is empty, fall back to the user field
                if (requestedUsername.isEmpty()) {
                    requestedUsername = initialMsg.getUser();
                }
                
                // Check username for profanity - reject instead of filtering
                if (!swearFilter.isClean(requestedUsername)) {
                    // Send error about inappropriate username
                    Message errorMsg = new Message("Username contains inappropriate content. Please choose another username.", "Server");
                    sendMessageToClient(errorMsg);
                    continue; // Try again with a new username
                }
                
                // Check if the username already exists
                if (pool.findClientByUsername(requestedUsername) != null) {
                    // Username already exists, send an error message
                    Message errorMsg = new Message("Username '" + requestedUsername + "' already exists. Please try another username.", "Server");
                    sendMessageToClient(errorMsg);
                } else {
                    // Username is available
                    username = requestedUsername;
                    isRegistered = true;
                    registrationSuccessful = true;
                    System.out.println("User registered: " + username); // Log registration
                    
                    // Send confirmation message back to the client
                    Message confirm = new Message("Successfully registered as: " + username, "Server");
                    sendMessageToClient(confirm);
                    
                    // Add client to the connection pool
                    pool.addClient(this);
                }
            } else {
                // Handle non-REGISTER initial message
                String requestedUsername = initialMsg.getUser();
                
                // Check username for profanity - reject instead of filtering
                if (!swearFilter.isClean(requestedUsername)) {
                    // Send error about inappropriate username
                    Message errorMsg = new Message("Username contains inappropriate content. Please choose another username.", "Server");
                    sendMessageToClient(errorMsg);
                    continue; // Try again with a new username
                }
                
                if (pool.findClientByUsername(requestedUsername) != null) {
                    Message errorMsg = new Message("Username '" + requestedUsername + "' already exists. Please try another username.", "Server");
                    sendMessageToClient(errorMsg);
                    // Try again with a new username
                } else {
                    username = requestedUsername;
                    isRegistered = true;
                    registrationSuccessful = true;
                    System.out.println("User registered (fallback): " + username);
                    
                    Message confirm = new Message("Successfully registered as: " + username, "Server");
                    sendMessageToClient(confirm);
                    
                    pool.addClient(this);
                }
            }
        }
    }
    
    private void handleClientCommunication() throws IOException, ClassNotFoundException {
        // Main loop to read further messages from the client
        while (true) {
            Message msg = (Message) inStream.readObject();
            String body = msg.getMessageBody();

            // Check for exit commands
            if (body.equalsIgnoreCase("/exit")) {
                try (socket) {
                    pool.removeClient(this);
                }
                System.out.println(username + " disconnected.");
                break;
            }

            // Handle register command properly preserving spaces in username
            if (body.startsWith("/register ")) {
                String newUsername = body.substring("/register ".length()).trim();
                
                // Check if new username is empty
                if (newUsername.isEmpty()) {
                    sendMessageToClient(new Message("Username cannot be empty.", "Server"));
                    continue;
                }
                
                // Check username for profanity - reject instead of filtering
                if (!swearFilter.isClean(newUsername)) {
                    // Send error about inappropriate username
                    sendMessageToClient(new Message("Username contains inappropriate content. Please choose another username.", "Server"));
                    continue; // Skip to next message
                }
                
                ServerHandler existingUser = pool.findClientByUsername(newUsername);
                
                if (existingUser != null && existingUser != this) {
                    // Username already exists and it's not this user - send an error message
                    sendMessageToClient(new Message("Username '" + newUsername + "' already exists. Please try another username.", "Server"));
                } else if (existingUser == this) {
                    // User is trying to register with their current username
                    sendMessageToClient(new Message("You are already registered as: " + newUsername, "Server"));
                } else {
                    // Valid new username
                    if (!isRegistered) {
                        pool.addClient(this);
                        isRegistered = true;
                    } else {
                        // Create a clean server announcement without filtering
                        String announcement = "User " + username + " has re-registered as: " + newUsername;
                        // Use a direct broadcast that won't filter server messages
                        Message serverMsg = new Message(announcement, "Server");
                        pool.broadcast(serverMsg, this);
                    }
                    username = newUsername;
                    sendMessageToClient(new Message("Successfully registered as: " + username, "Server"));
                }
                continue;
            }
            
            try (Scanner commandScanner = new Scanner(body)) {
                if (commandScanner.hasNext()) {
                    String command = commandScanner.next().toLowerCase();
                    
                    // Command handling logic
                    switch (command) {
                        case "/topic" -> {
                            if (commandScanner.hasNext()) {
                                String subCommand = commandScanner.next().toLowerCase();
                                String args = commandScanner.hasNextLine() ? commandScanner.nextLine().trim() : "";
                                String response = topicHandler.processTopicCommand(subCommand, args, this);
                                sendMessageToClient(new Message(response, "Server"));
                            } else {
                                sendMessageToClient(new Message("Please specify a topic command: /topic <create|subscribe|unsubscribe|list> [args]", "Server"));
                            }
                        }
                        case "/topics" -> {
                            // old command for backward compatibility
                            String response = topicHandler.listTopics();
                            sendMessageToClient(new Message(response, "Server"));
                        }
                        case "/user" -> {
                            if (commandScanner.hasNext()) {
                                String subCommand = commandScanner.next().toLowerCase();
                                switch (subCommand) {
                                    case "list" -> {
                                        // Get and send list of online users
                                        String response = pool.listUsers();
                                        sendMessageToClient(new Message(response, "Server"));
                                    }
                                    case "count" -> {
                                        // Get and send count of online users
                                        String response = pool.getUserCount();
                                        sendMessageToClient(new Message(response, "Server"));
                                    }
                                    default -> sendMessageToClient(new Message("Invalid user command. Try '/user list' or '/user count'", "Server"));
                                }
                            } else {
                                sendMessageToClient(new Message("Please specify a user command: /user <list|count>", "Server"));
                            }
                        }
                        case "/group" -> {
                            if (commandScanner.hasNext()) {
                                String subCommand = commandScanner.next().toLowerCase();
                                String args = commandScanner.hasNextLine() ? commandScanner.nextLine().trim() : "";
                                String response = chatGroup.processGroupCommand(subCommand, args, this);
                                sendMessageToClient(new Message(response, "Server"));
                            } else {
                                sendMessageToClient(new Message("Please specify a group command: /group <create|join|leave|remove|list> [args]", "Server"));
                            }
                        }
                        case "/create" -> { // old command
                            if (commandScanner.hasNext()) {
                                String groupName = commandScanner.next();
                                String response = chatGroup.createGroup(groupName);
                                sendMessageToClient(new Message(response, "Server"));
                            }
                        }
                        case "/join" -> { // old command
                            if (commandScanner.hasNext()) {
                                String groupName = commandScanner.next();
                                String response = chatGroup.joinGroup(groupName, this);
                                currentGroup = groupName;
                                sendMessageToClient(new Message(response, "Server"));
                            }
                        }
                        case "/leave" -> { // old command
                            if (commandScanner.hasNext()) {
                                String groupName = commandScanner.next();
                                String response = chatGroup.leaveGroup(groupName, this);
                                if (currentGroup.equalsIgnoreCase(groupName)) {
                                    currentGroup = "";
                                }
                                sendMessageToClient(new Message(response, "Server"));
                            }
                        }
                        case "/remove" -> { // old command
                            if (commandScanner.hasNext()) {
                                String groupName = commandScanner.next();
                                String response = chatGroup.removeGroup(groupName, this);
                                sendMessageToClient(new Message(response, "Server"));
                            }
                        }
                        case "/send" -> { //Send message to user or group
                            if (commandScanner.hasNext()) {
                                String targetType = commandScanner.next().toLowerCase();
                                
                                if (targetType.equals("user") || targetType.equals("group")) {
                                    // New command format: /send user|group <target> <message>
                                    if (commandScanner.hasNext()) {
                                        String target = commandScanner.next();
                                        String text = commandScanner.hasNextLine() ? commandScanner.nextLine().trim() : "";
                                        
                                        // Filter the message content
                                        text = swearFilter.filter(text);
                                        
                                        if (targetType.equals("group")) {
                                            if (chatGroup.groupExists(target)) {
                                                chatGroup.sendToGroup(target, new Message(text, username), this);
                                            } else {
                                                sendMessageToClient(new Message("Group " + target + " not found.", "Server"));
                                            }
                                        } else { // user
                                            ServerHandler recipient = pool.findClientByUsername(target);
                                            if (recipient != null) {
                                                Message directMsg = new Message("PRIVATE MESSAGE | " + username + ": " + text, "");
                                                recipient.sendMessageToClient(directMsg);
                                                sendMessageToClient(new Message("Message sent to user: " + target, "Server"));
                                            } else {
                                                sendMessageToClient(new Message("User " + target + " not found.", "Server"));
                                            }
                                        }
                                    } else {
                                        sendMessageToClient(new Message("Please specify a target: /send " + targetType + " <target> <message>", "Server"));
                                    }
                                } else {
                                    // old format for backward compatibility: /send <target> <message>
                                    String target = targetType; // In this case, targetType is actually the target
                                    String text = commandScanner.hasNextLine() ? commandScanner.nextLine().trim() : "";
                                    
                                    // Filter the message content
                                    text = swearFilter.filter(text);
                                    
                                    if (chatGroup.groupExists(target)) {
                                        //Send message to matching group name
                                        chatGroup.sendToGroup(target, new Message(text, username), this);
                                    } else {
                                        //If no match send message to username
                                        ServerHandler recipient = pool.findClientByUsername(target);
                                        if (recipient != null) {
                                            Message directMsg = new Message("PRIVATE MESSAGE | " + username + ": " + text, "");
                                            recipient.sendMessageToClient(directMsg);
                                        } else {
                                            sendMessageToClient(new Message("User or group " + target + " not found.", "Server"));
                                        }
                                    }
                                }
                            } else {
                                sendMessageToClient(new Message("Please specify a target type (user/group): /send <user|group> <target> <message>", "Server"));
                            }
                        }
                        case "/register" -> {
                            if (commandScanner.hasNext()) {
                                String newUsername = commandScanner.next();
                                
                                // Check username for profanity - reject instead of filtering
                                if (!swearFilter.isClean(newUsername)) {
                                    // Send error about inappropriate username
                                    sendMessageToClient(new Message("Username contains inappropriate content. Please choose another username.", "Server"));
                                    commandScanner.close();
                                    continue; // Skip to next message
                                }
                                
                                ServerHandler existingUser = pool.findClientByUsername(newUsername);
                                
                                if (existingUser != null && existingUser != this) {
                                    // Username already exists and it's not this user - send an error message
                                    sendMessageToClient(new Message("Username '" + newUsername + "' already exists. Please try another username.", "Server"));
                                } else if (existingUser == this) {
                                    // User is trying to register with their current username
                                    sendMessageToClient(new Message("You are already registered as: " + newUsername, "Server"));
                                } else {
                                    // Valid new username
                                    if (!isRegistered) {
                                        pool.addClient(this);
                                        isRegistered = true;
                                    } else {
                                        // Announce re-registration
                                        String announcement = "User " + username + " has re-registered as: " + newUsername;
                                        pool.broadcast(new Message(announcement, "Server"), this);
                                    }
                                    username = newUsername;
                                    sendMessageToClient(new Message("Successfully registered as: " + username, "Server"));
                                }
                            } else {
                                // No username provided with the /register command
                                sendMessageToClient(new Message("Please specify a username: /register <username>", "Server"));
                            }
                        }
                        case "/unregister" -> {
                            isRegistered = false;
                            pool.removeClient(this);
                            System.out.println("User unregistered: " + username);
                            sendMessageToClient(new Message("You have been unregistered. Register to chat again.", "Server"));
                        }
                        case "/name" -> {
                            sendMessageToClient(new Message("Your current username: " + username, "Server"));
                        }
                        default -> {
                            // Filter the message content for regular messages
                            String filteredBody = swearFilter.filter(body);
                            
                            //Show message to clients
                            if (!currentGroup.isEmpty()) { //If in a group, send the message to the group
                                chatGroup.sendToGroup(currentGroup, new Message(filteredBody, username), this);
                            } else { //If not in a group send the message to the global chat
                                pool.broadcast(new Message(filteredBody, username), this);
                            }
                            topicHandler.notifySubscribers(new Message(filteredBody, username), this);
                        }
                    }
                }
            }
        }
    }

    public void sendMessageToClient(Message msg) {
        try {
            outStream.writeObject(msg);
            outStream.flush();
        } catch (IOException e) {
            System.err.println("Error sending message to " + username + ": " + e.getMessage());
            pool.removeClient(this); //Remove client to prevent future errors
            //Optionally, close the socket and streams here.
        }
    }
}
