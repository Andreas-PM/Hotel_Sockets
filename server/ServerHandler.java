package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;
import shared.Message;
import shared.SwearFilter;

public class ServerHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;
    private ConnectionPool pool;
    private ChatGroup chatGroup;
    private TopicHandler topicHandler;
    private String username = "Anonymous";
    private String currentGroup = "";
    private boolean isRegistered = false;
    private SwearFilter swearFilter = new SwearFilter(); // Using shared SwearFilter

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
        }
    }

    public String getUsername() {
        return username;
    }

    public String getCurrentGroup() { return currentGroup; }

    @Override
    public void run() {
        try {
            // Process user registration until successful
            processInitialRegistration();
            
            // Main communication loop
            handleClientCommunication();
            
        } catch (Exception e) {
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
            Scanner scanner = new Scanner(initialBody);
            
            if (scanner.hasNext()) {
                String command = scanner.next();
                if (command.equalsIgnoreCase("REGISTER")) {
                    String requestedUsername = scanner.hasNext() ? scanner.next() : initialMsg.getUser();
                    
                    // Check username for profanity - reject instead of filtering
                    if (!swearFilter.isClean(requestedUsername)) {
                        // Send error about inappropriate username
                        Message errorMsg = new Message("Username contains inappropriate content. Please choose another username.", "Server");
                        sendMessageToClient(errorMsg);
                        scanner.close();
                        continue; // Try again with a new username
                    }
                    
                    // Check if the username already exists
                    if (pool.findClientByUsername(requestedUsername) != null) {
                        // Username already exists, send an error message
                        Message errorMsg = new Message("Username '" + requestedUsername + "' already exists. Please try another username.", "Server");
                        sendMessageToClient(errorMsg);
                        scanner.close();
                        continue; // Try again with a new username
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
                        scanner.close();
                        continue; // Try again with a new username
                    }
                    
                    if (pool.findClientByUsername(requestedUsername) != null) {
                        Message errorMsg = new Message("Username '" + requestedUsername + "' already exists. Please try another username.", "Server");
                        sendMessageToClient(errorMsg);
                        scanner.close();
                        continue; // Try again with a new username
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
            } else {
                // Empty message case - reject with error
                Message errorMsg = new Message("Invalid registration. Please provide a username.", "Server");
                sendMessageToClient(errorMsg);
            }
            scanner.close();
        }
    }
    
    private void handleClientCommunication() throws IOException, ClassNotFoundException {
        // Main loop to read further messages from the client
        while (true) {
            Message msg = (Message) inStream.readObject();
            String body = msg.getMessageBody();

            // Check for exit commands
            if (body.equalsIgnoreCase("exit") || body.equalsIgnoreCase("/exit")) {
                pool.removeClient(this);
                socket.close();
                System.out.println(username + " disconnected.");
                break;
            }

            Scanner commandScanner = new Scanner(body);
            if (commandScanner.hasNext()) {
                String command = commandScanner.next().toLowerCase();

                // Command handling logic
                if (command.equals("/topic")) {
                    if (commandScanner.hasNext()) {
                        String topic = commandScanner.next();
                        String response = topicHandler.createTopic(topic);
                        sendMessageToClient(new Message(response, "Server"));
                    }
                } else if (command.equals("/subscribe")) {
                    if (commandScanner.hasNext()) {
                        String topic = commandScanner.next();
                        String response = topicHandler.subscribe(topic, this);
                        sendMessageToClient(new Message(response, "Server"));
                    }
                } else if (command.equals("/unsubscribe")) {
                    if (commandScanner.hasNext()) {
                        String topic = commandScanner.next();
                        String response = topicHandler.unsubscribe(topic, this);
                        sendMessageToClient(new Message(response, "Server"));
                    }
                } else if (command.equals("/topics")) {
                    String response = topicHandler.listTopics();
                    sendMessageToClient(new Message(response, "Server"));
                } else if (command.equals("/create")) { //Create a new group
                    if (commandScanner.hasNext()) {
                        String groupName = commandScanner.next();
                        String response = chatGroup.createGroup(groupName);
                        sendMessageToClient(new Message(response, "Server"));
                    }
                } else if (command.equals("/join")) { //Join a group
                    if (commandScanner.hasNext()) {
                        String groupName = commandScanner.next();
                        String response = chatGroup.joinGroup(groupName, this);
                        currentGroup = groupName;
                        sendMessageToClient(new Message(response, "Server"));
                    }
                } else if (command.equals("/leave")) { //Leave a group
                    if (commandScanner.hasNext()) {
                        String groupName = commandScanner.next();
                        String response = chatGroup.leaveGroup(groupName, this);
                        if(currentGroup.equalsIgnoreCase(groupName)) {
                            currentGroup = "";
                        }
                        sendMessageToClient(new Message(response, "Server"));
                    }
                } else if (command.equals("/remove")) { //Remove a group
                    if (commandScanner.hasNext()) {
                        String groupName = commandScanner.next();
                        String response = chatGroup.removeGroup(groupName, this);
                        sendMessageToClient(new Message(response, "Server"));
                    }
                } else if (command.equals("/send")) { //Send message to user or group
                    if (commandScanner.hasNext()) {
                        String target = commandScanner.next();
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
                } else if (command.equals("/register")) {
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
                } else if (command.equals("/unregister")) {
                    isRegistered = false;
                    pool.removeClient(this);
                    System.out.println("User unregistered: " + username);
                    sendMessageToClient(new Message("You have been unregistered. Register to chat again.", "Server"));
                } else {
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
            commandScanner.close();
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
