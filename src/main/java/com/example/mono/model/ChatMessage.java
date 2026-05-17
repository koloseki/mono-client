package com.example.mono.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
    private String id;
    private String sender;
    private String content;
    private MessageType type;
    private String roomId;
    private long timestamp;

    private String fileUrl;
    private String fileName;


    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        SYSTEM
    }
}