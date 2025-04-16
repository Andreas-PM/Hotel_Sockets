package server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import shared.Message;
import shared.SwearFilter;

public class ChatGroup {
    private final HashMap<String, Set<ServerHandler>> groups;
    private final SwearFilter swearFilter = new SwearFilter();

    public ChatGroup() {
        groups = new HashMap<>();
    }

    public synchronized String createGroup(String groupName) { //Creating a new group
        if (groups.containsKey(groupName)) {
            return "Group " + groupName + " already exists.";
        } else {
            groups.put(groupName, new HashSet<>());
            return "Group " + groupName + " created.";
        }
    }

    public synchronized String joinGroup(String groupName, ServerHandler client) { //Joining a group
        if (!groups.containsKey(groupName)) {
            return "Group " + groupName + " does not exist.";
        }
        groups.get(groupName).add(client);
        return "Joined group " + groupName + ".";
    }

    public synchronized String leaveGroup(String groupName, ServerHandler client) { //Leaving a group
        if (!groups.containsKey(groupName)) {
            return "Group " + groupName + " does not exist.";
        }
        groups.get(groupName).remove(client);
        return "Left group " + groupName + ".";
    }

    public synchronized String removeGroup(String groupName, ServerHandler client) { //Removing a group
        // Optionally check for admin rights here.
        if (!groups.containsKey(groupName)) {
            return "Group " + groupName + " does not exist.";
        }
        groups.remove(groupName);
        return "Group " + groupName + " removed.";
    }
    
    public synchronized String listGroups() { //List all available groups
        if (groups.isEmpty()) {
            return "No groups available.";
        }
        return "Available groups: " + String.join(", ", groups.keySet());
    }

    public synchronized boolean groupExists(String groupName) { //Check group exists
        return groups.containsKey(groupName);
    }

    public synchronized void sendToGroup(String groupName, Message msg, ServerHandler sender) { //Send message to group
        if (!groups.containsKey(groupName)) {
            sender.sendMessageToClient(new Message("Group " + groupName + " does not exist.", "Server"));
            return;
        }
        
        // Apply swear filter to message content
        String filteredMessageBody = swearFilter.filter(msg.getMessageBody());
        String groupPrefix = groupName.toUpperCase() + " | " + msg.getUser() + ": " + filteredMessageBody; //Adds the group name in front of the message
        Message groupMsg = new Message(groupPrefix, "");
        
        for (ServerHandler client : groups.get(groupName)) {
            if (client != sender) {
                client.sendMessageToClient(groupMsg);
            }
        }
    }
    
    public synchronized String processGroupCommand(String subCommand, String args, ServerHandler client) {
        switch (subCommand) {
            case "create" -> {
                if (args == null || args.isEmpty()) {
                    return "Please specify a group name: /group create <groupName>";
                }
                return createGroup(args);
            }
            case "join" -> {
                if (args == null || args.isEmpty()) {
                    return "Please specify a group name: /group join <groupName>";
                }
                String response = joinGroup(args, client);
                if (response.startsWith("Joined")) {
                    client.setCurrentGroup(args);
                }
                return response;
            }
            case "leave" -> {
                if (args == null || args.isEmpty()) {
                    return "Please specify a group name: /group leave <groupName>";
                }
                String response = leaveGroup(args, client);
                if (response.startsWith("Left") && args.equalsIgnoreCase(client.getCurrentGroup())) {
                    client.setCurrentGroup("");
                }
                return response;
            }
            case "remove" -> {
                if (args == null || args.isEmpty()) {
                    return "Please specify a group name: /group remove <groupName>";
                }
                return removeGroup(args, client);
            }
            case "list" -> {
                return listGroups();
            }
            default -> {
                return "Invalid group command. Available options: create, join, leave, remove, list";
            }
        }
    }
}