package server;
import java.util.ArrayList;
import java.util.List;
import shared.Message;

public class ConnectionPool { // Sourced from Practical 4
    private List<ServerHandler> connects = new ArrayList<>();

    public void addConnects(ServerHandler csh) {
        connects.add(csh);
    }

    public void broadcast(Message msg) { // displays messages
        for (ServerHandler cnn:connects){
            if (!cnn.getClientName().equals(msg.getUser())){
                cnn.sendMessageToClients(msg);
            }
        }
    }

    public void removeUser(ServerHandler csh) {
        connects.remove(csh); // removes chatserverhandler
    }
}
