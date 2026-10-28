package com.vernu.sms;

public class Message {
    public String type;
    public String id;
    public String to;
    public String message;
    public long timestamp;

    public Message() {}

    public Message(String type, String id, String to, String message, long timestamp) {
        this.type = type;
        this.id = id;
        this.to = to;
        this.message = message;
        this.timestamp = timestamp;
    }
}