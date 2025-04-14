package server;
import java.util.ArrayList;
import java.util.List;
import shared.Message;

public class ConnectionPool {
    private List<ServerHandler> clients;

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
        Message globalMsg = new Message("GLOBAL | " + msg.getUser() + ": " + msg.getMessageBody(), "");
        for (ServerHandler client : clients) {
            //Only send to clients that are not in a group (global chat)
            if (client != sender &&
                    (client.getCurrentGroup() == null || client.getCurrentGroup().isEmpty())) {
                client.sendMessageToClient(globalMsg);
            }
        }
        //Log the broadcast on the server side
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
}
