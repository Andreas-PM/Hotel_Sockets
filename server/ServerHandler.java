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
    private ChatGroup chatGroup;
    private TopicHandler topicHandler;
    private String username = "Anonymous";
    private String currentGroup = "";
    private boolean isRegistered = false;

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
            //Process initial registration using a command like "REGISTER myname"
            Message initialMsg = (Message) inStream.readObject();
            String initialBody = initialMsg.getMessageBody();
            Scanner scanner = new Scanner(initialBody);
            if (scanner.hasNext()) {
                String command = scanner.next();
                if (command.equalsIgnoreCase("REGISTER")) {
                    if (scanner.hasNext()) {
                        username = scanner.next();
                        isRegistered = true;
                        System.out.println("User registered: " + username); //Log registration
                        //Send confirmation message back to the client
                        Message confirm = new Message("Registration successful as " + username, "Server");
                        sendMessageToClient(confirm);
                    } else {
                        //use the provided username from the Message object.
                        username = initialMsg.getUser();
                        isRegistered = true;
                        System.out.println("User registered (fallback): " + username);
                    }
                } else {
                    //use the provided username from the Message object.
                    username = initialMsg.getUser();
                    isRegistered = true;
                    System.out.println("User registered (fallback): " + username);
                }
            }
            scanner.close();

            //Main loop to read further messages from the client.
            while (true) {
                Message msg = (Message) inStream.readObject();
                String body = msg.getMessageBody();

                //Check for exit commands.
                if (body.equalsIgnoreCase("exit") || body.equalsIgnoreCase("/exit")) {
                    pool.removeClient(this);
                    socket.close();
                    System.out.println(username + " disconnected.");
                    break;
                }

                Scanner commandScanner = new Scanner(body);
                if (commandScanner.hasNext()) {
                    String command = commandScanner.next();

                    if (command.equalsIgnoreCase("TOPIC")) {
                        if (commandScanner.hasNext()) {
                            String topic = commandScanner.next();
                            String response = topicHandler.createTopic(topic);
                            sendMessageToClient(new Message(response, "Server"));
                        }
                    } else if (command.equalsIgnoreCase("SUBSCRIBE")) {
                        if (commandScanner.hasNext()) {
                            String topic = commandScanner.next();
                            String response = topicHandler.subscribe(topic, this);
                            sendMessageToClient(new Message(response, "Server"));
                        }
                    } else if (command.equalsIgnoreCase("UNSUBSCRIBE")) {
                        if (commandScanner.hasNext()) {
                            String topic = commandScanner.next();
                            String response = topicHandler.unsubscribe(topic, this);
                            sendMessageToClient(new Message(response, "Server"));
                        }
                    } else if (command.equalsIgnoreCase("TOPICS")) {
                        String response = topicHandler.listTopics();
                        sendMessageToClient(new Message(response, "Server"));
                    }

                    if (command.equalsIgnoreCase("CREATE")) { //Create a new group
                        if (commandScanner.hasNext()) {
                            String groupName = commandScanner.next();
                            String response = chatGroup.createGroup(groupName);
                            sendMessageToClient(new Message(response, "Server"));
                        }
                    } else if (command.equalsIgnoreCase("JOIN")) { //Join a group
                        if (commandScanner.hasNext()) {
                            String groupName = commandScanner.next();
                            String response = chatGroup.joinGroup(groupName, this);
                            currentGroup = groupName;
                            sendMessageToClient(new Message(response, "Server"));
                        }
                    } else if (command.equalsIgnoreCase("LEAVE")) { //Leave a group
                        if (commandScanner.hasNext()) {
                            String groupName = commandScanner.next();
                            String response = chatGroup.leaveGroup(groupName, this);
                            if(currentGroup.equalsIgnoreCase(groupName)) {
                                currentGroup = "";
                            }
                            sendMessageToClient(new Message(response, "Server"));
                        }
                    } else if (command.equalsIgnoreCase("REMOVE")) { //Remove a group
                        if (commandScanner.hasNext()) {
                            String groupName = commandScanner.next();
                            String response = chatGroup.removeGroup(groupName, this);
                            sendMessageToClient(new Message(response, "Server"));
                        }
                    } else if (command.equalsIgnoreCase("SEND")) { //Send message to user or group
                        if (commandScanner.hasNext()) {
                            String target = commandScanner.next();
                            String text = commandScanner.hasNextLine() ? commandScanner.nextLine().trim() : "";
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
                    } else if (command.equalsIgnoreCase("REGISTER")) {
                        if (commandScanner.hasNext()) {
                            username = commandScanner.next();
                            if (!isRegistered) {
                                pool.addClient(this);
                            }
                            isRegistered = true;
                            System.out.println("User re-registered as: " + username);
                            sendMessageToClient(new Message("Successfully registered as: " + username, "Server"));
                        }
                    } else if (command.equalsIgnoreCase("UNREGISTER")) {
                        isRegistered = false;
                        pool.removeClient(this);
                        System.out.println("User unregistered: " + username);
                        sendMessageToClient(new Message("You have been unregistered. Register to chat again.", "Server"));
                    } else {
                        //Show message to clients
                        if (!currentGroup.isEmpty()) { //If in a group, send the message to the group
                            chatGroup.sendToGroup(currentGroup, new Message(body, username), this);
                        } else { //If not in a group send the message to the global chat
                            pool.broadcast(new Message(body, username), this);
                        }
                        topicHandler.notifySubscribers(new Message(body, username), this);
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
            pool.removeClient(this); //Remove client to prevent future errors
            //Optionally, close the socket and streams here.
        }
    }

}
