package shared;

import java.io.Serializable;


// From Practical 4 multiClientChat
public class Message implements Serializable {
    private final String messageBody;
    private final String user;

    public Message(String messageBody, String username) {
        this.messageBody = messageBody;
        this.user = username;
    }

    public String getMessageBody() {
        return this.messageBody;
    }

    public String getUser() {
        return this.user;
    }

    @Override
    public String toString(){
        return this.user + ": " + this.messageBody;
    }
}
