package server;

import java.util.*;
import shared.Message;
import shared.SwearFilter;

public class TopicHandler {
    private final Set<String> topics = new HashSet<>();
    private final Map<String, Set<ServerHandler>> subscriptions = new HashMap<>();
    private final SwearFilter swearFilter = new SwearFilter();

    public synchronized String createTopic(String topic) {
        if (topics.add(topic.toLowerCase())) {
            subscriptions.put(topic.toLowerCase(), new HashSet<>());
            return "Topic: " + topic + " created";
        }
        return "Topic: " + topic + " already exists ";
    }
    
    public synchronized String subscribe(String topic, ServerHandler handler) {
        topic = topic.toLowerCase();
        if(!topics.contains(topic)) {
            return "Topic " + topic + " does not exist ";
        }
        subscriptions.get(topic).add(handler);
        return "Topic " + topic + " subscribed";
    }

    public synchronized String unsubscribe(String topic, ServerHandler handler) {
        topic = topic.toLowerCase();
        if(!topics.contains(topic)) {
            return "Topic " + topic + " does not exist ";
        }
        subscriptions.get(topic).remove(handler);
        return "Topic " + topic + " unsubscribed";
    }

    public synchronized String listTopics() {
        if (topics.isEmpty()) {
            return "No topic subscribed";
        }
        return "List of topics: " + topics;
    }

    public synchronized void notifySubscribers(Message message, ServerHandler sender) {
        //create topics and notify the sender if a new topic is created
        Set<String> hashtags = extractHashtags(message.getMessageBody());
        for (String tag : hashtags) {
            //Create topic and get the response message
            String topicResponse = createTopic(tag);
            //If the topic was created, send the confirmation
            if (topicResponse.contains("created")) {
                sender.sendMessageToClient(new Message(topicResponse, "Server"));
            }
        }

        // Filter the message before broadcasting to subscribers
        String filteredMessage = swearFilter.filter(message.getMessageBody());
        
        //Check each topic for a match in the message text
        String messageTextLower = filteredMessage.toLowerCase();
        for (Map.Entry<String, Set<ServerHandler>> entry : subscriptions.entrySet()) {
            String topic = entry.getKey();  // stored as lower case
            //Check if the message text contains the topic
            if (messageTextLower.contains(topic)) {
                String formatted = topic.toUpperCase() + " | " + message.getUser() + ": " + filteredMessage;
                for (ServerHandler subscriber : entry.getValue()) {
                    subscriber.sendMessageToClient(new Message(formatted, ""));
                }
            }
        }
    }

    private Set<String> extractHashtags(String messageBody) {
        Set<String> hashtags = new HashSet<>();
        for (String word : messageBody.split("\\s+")) {
            if (word.startsWith("#") && word.length() > 1) {
                hashtags.add(word.substring(1).toLowerCase());
            }
        }
        return hashtags;
    }
    
    public synchronized String processTopicCommand(String subCommand, String args, ServerHandler handler) {
        switch (subCommand) {
            case "create" -> {
                if (args == null || args.isEmpty()) {
                    return "Please specify a topic name: /topic create <topicName>";
                }
                return createTopic(args);
            }
            case "subscribe" -> {
                if (args == null || args.isEmpty()) {
                    return "Please specify a topic name: /topic subscribe <topicName>";
                }
                return subscribe(args, handler);
            }
            case "unsubscribe" -> {
                if (args == null || args.isEmpty()) {
                    return "Please specify a topic name: /topic unsubscribe <topicName>";
                }
                return unsubscribe(args, handler);
            }
            case "list" -> {
                return listTopics();
            }
            default -> {
                return "Invalid topic command. Available options: create, subscribe, unsubscribe, list";
            }
        }
    }
}
