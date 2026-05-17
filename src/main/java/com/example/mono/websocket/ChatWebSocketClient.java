package com.example.mono.websocket;

import com.example.mono.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ChatWebSocketClient extends WebSocketClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String token;
    private final String roomId;
    private final String username;
    private final Consumer<String> messageHandler;
    private final CountDownLatch stompReadyLatch = new CountDownLatch(1);
    private final AtomicInteger subCounter = new AtomicInteger(0);

    public ChatWebSocketClient(URI serverUri, String token, String roomId, String username, Consumer<String> messageHandler) {
        super(serverUri);
        this.token = token;
        this.roomId = roomId;
        this.username = username;
        this.messageHandler = messageHandler;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("accept-version", "1.2");
        headers.put("heart-beat", "0,0");
        headers.put("X-Auth-Token", token);
        sendFrame(new StompFrame("CONNECT", headers, null));
    }

    @Override
    public void onMessage(String raw) {
        StompFrame frame = StompFrame.parse(raw);
        switch (frame.getCommand()) {
            case "CONNECTED" -> onStompConnected();
            case "MESSAGE"   -> onStompMessage(frame);
            case "ERROR"     -> System.err.println("\n[STOMP ERROR] " + frame.getBody());
        }
    }

    private void onStompConnected() {
        Map<String, String> sub = new LinkedHashMap<>();
        sub.put("id", "sub-" + subCounter.getAndIncrement());
        sub.put("destination", "/topic/room." + roomId);
        sendFrame(new StompFrame("SUBSCRIBE", sub, null));
        stompReadyLatch.countDown();
    }

    public void sendJoin() {
        sendSystemMessage(ChatMessage.MessageType.JOIN);
    }

    private void onStompMessage(StompFrame frame) {
        try {
            ChatMessage msg = objectMapper.readValue(frame.getBody(), ChatMessage.class);
            String line = switch (msg.getType()) {
                case CHAT   -> "[" + msg.getSender() + "]: " + msg.getContent();
                case JOIN   -> "[SYSTEM] " + msg.getSender() + " joined the room";
                case LEAVE  -> "[SYSTEM] " + msg.getSender() + " left the room";
                case SYSTEM -> "[SYSTEM] " + msg.getContent();
            };
            messageHandler.accept(line);
        } catch (Exception e) {
            messageHandler.accept("[Error] Could not parse message: " + e.getMessage());
        }
    }

    public void sendChatMessage(String content, String roomId) {
        try {
            ChatMessage msg = new ChatMessage();
            msg.setSender(username);
            msg.setContent(content);
            msg.setType(ChatMessage.MessageType.CHAT);
            msg.setRoomId(roomId);
            msg.setTimestamp(System.currentTimeMillis());
            sendToApp(msg);
        } catch (Exception e) {
            System.err.println("[Error] Could not send message: " + e.getMessage());
        }
    }

    private void sendSystemMessage(ChatMessage.MessageType type) {
        try {
            ChatMessage msg = new ChatMessage();
            msg.setSender(username);
            msg.setType(type);
            msg.setRoomId(roomId);
            msg.setTimestamp(System.currentTimeMillis());
            sendToApp(msg);
        } catch (Exception e) {
            System.err.println("[Error] " + e.getMessage());
        }
    }

    private void sendToApp(ChatMessage msg) throws Exception {
        String json = objectMapper.writeValueAsString(msg);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("destination", "/app/chat.sendMessage");
        headers.put("content-type", "application/json");
        sendFrame(new StompFrame("SEND", headers, json));
    }

    private void sendFrame(StompFrame frame) {
        send(frame.serialize());
    }

    public boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
        return stompReadyLatch.await(timeout, unit);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("\nDisconnected: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[WebSocket Error] " + ex.getMessage());
    }
}
