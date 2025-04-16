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
        clients.add(client);
    }

    public synchronized void removeClient(ServerHandler client) {
        clients.remove(client);
    }

    public synchronized void broadcast(Message msg, ServerHandler sender) {
        // Apply swear filter to the message
        String filteredMessage = swearFilter.filter(msg.getMessageBody());
        Message globalMsg = new Message("GLOBAL | " + msg.getUser() + ": " + filteredMessage, "");
        
        for (ServerHandler client : clients) {
            //Only send to clients that are not in a group (global chat)
            if (client != sender &&
                    (client.getCurrentGroup() == null || client.getCurrentGroup().isEmpty())) {
                client.sendMessageToClient(globalMsg);
            }
        }
        //Log the broadcast on the server side
        System.out.println("Broadcast from " + msg.getUser() + ": " + filteredMessage);
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
