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

    public synchronized void notifySubscribers(Message message, ServerHandler handler) {
        Set<String> hashtags = extractHashtags(message.getMessageBody());

        for (String topic : hashtags) {
            createTopic(topic);
            Set<ServerHandler> subscribers = subscriptions.get(topic.toLowerCase());

            if (subscribers != null) {
                for (ServerHandler subscriber : subscribers) {
                    if(subscriber != null) {
                        subscriber.sendMessageToClient(new Message("#" + topic + " | " + message.getMessageBody(), message.getUser()));
                    }
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
