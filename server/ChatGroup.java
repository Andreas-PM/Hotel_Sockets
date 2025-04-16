package server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import shared.Message;
import shared.SwearFilter;

public class ChatGroup {
    private final Map<String, Set<ServerHandler>> groups;
    private final SwearFilter swearFilter = new SwearFilter();

    public ChatGroup() {
        groups = new HashMap<>();
    }

    public synchronized String createGroup(String groupName) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return "Group name cannot be empty.";
        }

        groupName = groupName.trim();
        if (groups.containsKey(groupName)) {
            return "Group '" + groupName + "' already exists.";
        }

        groups.put(groupName, new HashSet<>());
        return "Group '" + groupName + "' created successfully.";
    }

    public synchronized String joinGroup(String groupName, ServerHandler client) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return "Group name cannot be empty.";
        }

        groupName = groupName.trim();
        Set<ServerHandler> groupMembers = groups.get(groupName);
        if (groupMembers == null) {
            return "Group '" + groupName + "' does not exist.";
        }

        if (groupMembers.contains(client)) {
            return "You are already in group '" + groupName + "'.";
        }

        String oldGroup = client.getCurrentGroup();
        if (!oldGroup.isEmpty()) {
            leaveGroup(oldGroup, client);
        }

        groupMembers.add(client);
        client.setCurrentGroup(groupName);

        // Announce to group - DON'T filter system announcements
        String announcement = "User " + client.getUsername() + " joined group '" + groupName + "'.";
        sendGroupAnnouncement(groupName, new Message(announcement, "Server"), client);

        return "You joined group '" + groupName + "'.";
    }

    public synchronized String leaveGroup(String groupName, ServerHandler client) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return "Group name cannot be empty.";
        }

        groupName = groupName.trim();
        Set<ServerHandler> groupMembers = groups.get(groupName);
        if (groupMembers == null) {
            return "Group '" + groupName + "' does not exist.";
        }

        if (!groupMembers.contains(client)) {
            return "You are not in group '" + groupName + "'.";
        }

        groupMembers.remove(client);
        if (client.getCurrentGroup().equals(groupName)) {
            client.setCurrentGroup("");
        }

        // Announce to group - DON'T filter system announcements
        String announcement = "User " + client.getUsername() + " left group '" + groupName + "'.";
        sendGroupAnnouncement(groupName, new Message(announcement, "Server"), null);

        // Remove empty groups
        if (groupMembers.isEmpty()) {
            groups.remove(groupName);
            return "You left group '" + groupName + "'. Group was removed as it is now empty.";
        }

        return "You left group '" + groupName + "'.";
    }

    public synchronized String removeGroup(String groupName, ServerHandler client) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return "Group name cannot be empty.";
        }

        groupName = groupName.trim();
        if (!groups.containsKey(groupName)) {
            return "Group '" + groupName + "' does not exist.";
        }

        Set<ServerHandler> groupMembers = groups.get(groupName);
        // Announce to all group members before removing
        String announcement = "Group '" + groupName + "' has been removed by " + client.getUsername() + ".";
        sendGroupAnnouncement(groupName, new Message(announcement, "Server"), null);

        // Reset currentGroup for all members
        for (ServerHandler member : groupMembers) {
            if (member.getCurrentGroup().equals(groupName)) {
                member.setCurrentGroup("");
            }
        }

        groups.remove(groupName);
        return "Group '" + groupName + "' was removed.";
    }

    public synchronized String listGroups() {
        if (groups.isEmpty()) {
            return "No groups available.";
        }

        StringBuilder sb = new StringBuilder("Available groups:\n");
        for (Map.Entry<String, Set<ServerHandler>> entry : groups.entrySet()) {
            String groupName = entry.getKey();
            int memberCount = entry.getValue().size();
            sb.append("- ").append(groupName).append(" (").append(memberCount).append(" members)\n");
        }

        return sb.toString().trim();
    }

    public synchronized boolean groupExists(String groupName) {
        return groups.containsKey(groupName);
    }

    public synchronized void sendToGroup(String groupName, Message msg, ServerHandler sender) {
        Set<ServerHandler> groupMembers = groups.get(groupName);
        if (groupMembers == null) {
            if (sender != null) {
                sender.sendMessageToClient(new Message("Group '" + groupName + "' does not exist.", "Server"));
            }
            return;
        }

        // Special handling for server messages - never filter them
        boolean isServerMessage = "Server".equals(msg.getUser());

        String prefix = "GROUP [" + groupName + "] | ";
        for (ServerHandler member : groupMembers) {
            if (member != sender) { // Don't send back to the sender
                if (isServerMessage) {
                    // Don't filter server messages
                    member.sendMessageToClient(new Message(prefix + msg.getUser() + ": " + msg.getMessageBody(), ""));
                } else {
                    // Regular message
                    String filteredMessageBody = swearFilter.filter(msg.getMessageBody());
                    member.sendMessageToClient(new Message(prefix + msg.getUser() + ": " + filteredMessageBody, ""));
                }
            }
        }
    }

    private void sendGroupAnnouncement(String groupName, Message msg, ServerHandler exclude) {
        Set<ServerHandler> groupMembers = groups.get(groupName);
        if (groupMembers == null) {
            return;
        }

        String prefix = "GROUP [" + groupName + "] | ";
        for (ServerHandler member : groupMembers) {
            if (member != exclude) {
                member.sendMessageToClient(new Message(prefix + msg.getUser() + ": " + msg.getMessageBody(), ""));
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
                return joinGroup(args, client);
            }
            case "leave" -> {
                if (args == null || args.isEmpty()) {
                    return "Please specify a group name: /group leave <groupName>";
                }
                return leaveGroup(args, client);
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