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

    public synchronized void broadcast(Message msg, ServerHandler sender) { // CHANGED: Now accepts a sender to skip broadcasting to self
        for (ServerHandler client : clients) {
            if (client != sender) {
                client.sendMessageToClient(msg);
            }
        }
        //Log the broadcast on the server side.
        System.out.println("Broadcast from " + msg.getUser() + ": " + msg.getMessageBody());
    }
}
