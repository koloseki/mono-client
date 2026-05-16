package com.example.mono.websocket;

import java.util.LinkedHashMap;
import java.util.Map;

public class StompFrame {

    private final String command;
    private final Map<String, String> headers;
    private final String body;

    public StompFrame(String command, Map<String, String> headers, String body) {
        this.command = command;
        this.headers = headers != null ? headers : new LinkedHashMap<>();
        this.body = body != null ? body : "";
    }

    public String getCommand() { return command; }
    public String getBody() { return body; }
    public String getHeader(String name) { return headers.get(name); }

    public static StompFrame parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new StompFrame("", null, null);
        }
        if (raw.endsWith("\0")) {
            raw = raw.substring(0, raw.length() - 1);
        }

        String[] parts = raw.split("\n\n", 2);
        String[] lines = parts[0].split("\n");
        String command = lines[0].trim();

        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).trim(), lines[i].substring(colon + 1).trim());
            }
        }

        String body = parts.length > 1 ? parts[1].trim() : "";
        return new StompFrame(command, headers, body);
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(command).append("\n");
        headers.forEach((k, v) -> sb.append(k).append(":").append(v).append("\n"));
        sb.append("\n");
        sb.append(body);
        sb.append("\0");
        return sb.toString();
    }
}
