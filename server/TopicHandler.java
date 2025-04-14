package server;

import java.util.*;
import shared.Message;

public class TopicHandler {
    private Set<String> topics = new HashSet<>();
    private Map<String, Set<ServerHandler>> subscriptions = new HashMap<>();

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
        if (topics.size() == 0) {
            return "No topic subscribed";
        }
        return "List of topics: " + topics;
    }

    // In server/TopicHandler.java
    // In server/TopicHandler.java
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

        //heck each topic for a match in the message text ---
        String messageTextLower = message.getMessageBody().toLowerCase();
        for (Map.Entry<String, Set<ServerHandler>> entry : subscriptions.entrySet()) {
            String topic = entry.getKey();  // sored as lower case
            //Check if the message text contains the topic
            if (messageTextLower.contains(topic)) {
                String formatted = topic.toUpperCase() + " | " + message.getUser() + ": " + message.getMessageBody();
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

}
