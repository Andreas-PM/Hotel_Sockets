package server;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import shared.Message;
import shared.SwearFilter;

public class ConnectionPool {
    private final List<ServerHandler> clients;
    private final SwearFilter swearFilter = new SwearFilter();

    public ConnectionPool() {
        clients = new ArrayList<>();
    }

    public synchronized void addClient(ServerHandler client) {
        // Check if client is already in the pool
        if (!clients.contains(client)) {
            clients.add(client);
            // Announce new client to all existing clients
            String username = client.getUsername();
            String announcement = "User " + username + " joined the chat.";
            // Don't filter server announcements - direct message construction
            Message announceMsg = new Message(announcement, "Server");
            for (ServerHandler existingClient : clients) {
                if (existingClient != client) { // Don't send to the new client
                    existingClient.sendMessageToClient(announceMsg);
                }
            }
        }
    }

    public synchronized void removeClient(ServerHandler client) {
        if (clients.remove(client)) {
            String username = client.getUsername();
            String announcement = "User " + username + " left the chat.";
            // Don't filter server announcements - direct message construction
            Message announceMsg = new Message(announcement, "Server");
            for (ServerHandler remainingClient : clients) {
                remainingClient.sendMessageToClient(announceMsg);
            }
        }
    }

    public synchronized void broadcast(Message msg, ServerHandler sender) {
        // Special handling for Server messages - never filter them
        boolean isServerMessage = msg.getUser() != null && msg.getUser().equals("Server");

        for (ServerHandler client : clients) {
            if (client != sender) {
                // If it's a server message, send it directly without filtering
                if (isServerMessage) {
                    Message serverMsg = new Message("GLOBAL | " + msg.getUser() + ": " + msg.getMessageBody(), "");
                    client.sendMessageToClient(serverMsg);
                } else {
                    // Regular user message, let the ServerHandler handle any needed filtering
                    String filteredMessage = swearFilter.filter(msg.getMessageBody());
                    Message userMsg = new Message("GLOBAL | " + msg.getUser() + ": " + filteredMessage, "");
                    client.sendMessageToClient(userMsg);
                }
            }
        }
        // Log the broadcast on the server side
        System.out.println("Broadcast from " + msg.getUser() + ": " + msg.getMessageBody());
    }

    public synchronized ServerHandler findClientByUsername(String username) {
        for (ServerHandler client : clients) {
            if (client.getUsername().equalsIgnoreCase(username)) {
                return client;
            }
        }
        return null;
    }
    
    /**
     * Gets a list of all online users
     * @return String containing a list of all online usernames
     */
    public synchronized String listUsers() {
        if (clients.isEmpty()) {
            return "No users online";
        }
        String userList = clients.stream()
                .map(ServerHandler::getUsername)
                .collect(Collectors.joining(", "));
        return "Users online = " + userList;
    }
    
    /**
     * Gets the count of all online users
     * @return String containing the count of online users
     */
    public synchronized String getUserCount() {
        int count = clients.size();
        return "Users online = " + count;
    }
}
