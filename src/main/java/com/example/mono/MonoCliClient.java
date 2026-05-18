package com.example.mono;

import com.example.mono.model.ChatMessage;
import com.example.mono.model.LoginRequest;
import com.example.mono.model.Room;
import com.example.mono.service.FileService;
import com.example.mono.service.RoomService;
import com.example.mono.util.AuthResponse;
import com.example.mono.websocket.ChatWebSocketClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;


public class MonoCliClient {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String WS_URL = "ws://localhost:8080/ws";

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String sessionToken = null;
    private static String username = null;


    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("/////////////////////////////////");
        System.out.println("        Mono Chat Client         ");
        System.out.println("/////////////////////////////////");

        while (sessionToken == null) {
            System.out.print("Login: ");
            String username = scanner.nextLine();

            System.out.print("Password: ");

            String password = scanner.nextLine();

            if (username.isBlank() || password.isBlank()) {
                System.out.println("[Error] The credentials cannot be empty\n");
                continue;
            }

            System.out.println("Connecting to the server...");
            boolean success = tryLogin(username, password);

            if (!success) {
                System.out.println("[Error] Invalid login or password, try again.\n");
            }
        }

        System.out.println("\n[Successful login]");
        System.out.println("Welcome, " + username + ".");

        RoomService roomService = new RoomService(BASE_URL, sessionToken);
        Room selectedRoom = selectRoom(scanner, roomService);

        if (selectedRoom == null) {
            System.out.println("You have to select a room to join.");
            scanner.close();
            return;
        }

        connectAndChat(selectedRoom, roomService);
        scanner.close();
    }

    private static boolean tryLogin(String username, String password) {
        try {
            LoginRequest requestBody = new LoginRequest(username, password);
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                AuthResponse authResponse = objectMapper.readValue(response.body(), AuthResponse.class);
                sessionToken = authResponse.getToken();
                MonoCliClient.username = username;
                return true;
            } else {
                return false;
            }

        } catch (Exception e) {
            System.out.println("[System Error] " + e.getMessage());
            return false;
        }
    }

    private static Room selectRoom(Scanner scanner, RoomService roomService) {
        try {
            System.out.println("\n--- Available rooms ---");
            List<Room> rooms = roomService.getRooms();

            if (rooms.isEmpty()) {
                System.out.println("No rooms available.");
                return null;
            }

            for (int i = 0; i < rooms.size(); i++) {
                Room room = rooms.get(i);
                System.out.println((i + 1) + ". " + room.getName() + " (" + room.getUserCount() + " users)");
            }

            System.out.print("\nSelect room number (or 0 to quit): ");
            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 0 || choice < 0 || choice > rooms.size()) {
                return null;
            }

            Room selectedRoom = rooms.get(choice - 1);
            System.out.println("Joining room: " + selectedRoom.getName() + "...");

            return roomService.joinRoom(selectedRoom.getId());

        } catch (Exception e) {
            System.err.println("[Error] Could not fetch room list: " + e.getMessage());
            return null;
        }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static void connectAndChat(Room room, RoomService roomService) {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();

            System.out.println("\nConnecting to WebSocket...");

            ChatWebSocketClient wsClient = new ChatWebSocketClient(
                    new URI(WS_URL),
                    sessionToken,
                    room.getId(),
                    username,
                    msg -> lineReader.printAbove(msg)
            );

            wsClient.connectBlocking();
            if (!wsClient.awaitReady(5, TimeUnit.SECONDS)) {
                System.out.println("[Error] STOMP handshake timed out.");
                wsClient.close();
                return;
            }

            lineReader.printAbove("\n--- You are in room: " + room.getName() + " ---");

            try {
                List<ChatMessage> history = roomService.getMessages(room.getId());
                if (!history.isEmpty()) {
                    for (ChatMessage msg : history) {
                        String time = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(msg.getTimestamp()), ZoneId.systemDefault()
                        ).format(TIME_FMT);
                        lineReader.printAbove("[" + time + "] [" + msg.getSender() + "]: " + msg.getContent());
                    }
                }
            } catch (Exception e) {
                lineReader.printAbove("[Warning] Could not load message history: " + e.getMessage());
            }

            wsClient.sendJoin();
            FileService fileService = new FileService(BASE_URL, sessionToken);
            lineReader.printAbove("[SYSTEM] Commands: /exit, /help, /upload <path>, /download <filename>");

            while (wsClient.isOpen()) {
                String input;
                try {
                    input = lineReader.readLine("> ");
                    terminal.writer().print("\033[1A\033[2K\r");
                    terminal.writer().flush();
                } catch (UserInterruptException | EndOfFileException e) {
                    wsClient.close();
                    break;
                }

                if (input.equalsIgnoreCase("/exit")) {
                    wsClient.close();
                    break;
                }

                if (input.equalsIgnoreCase("/help")) {
                    lineReader.printAbove("[SYSTEM] Commands: /exit, /help, /upload <path>, /download <filename>");
                    continue;
                }

                if (input.startsWith("/upload ")) {
                    String pathStr = input.substring(8).trim();
                    try {
                        Path filePath = Paths.get(pathStr);
                        lineReader.printAbove("[System] Uploading " + filePath.getFileName() + "...");
                        Map<String, String> result = fileService.upload(filePath);
                        wsClient.sendFileMessage(result.get("fileUrl"), result.get("originalName"), room.getId());
                        lineReader.printAbove("[System] Upload successful: " + result.get("originalName"));
                    } catch (Exception e) {
                        lineReader.printAbove("[Error] Upload failed: " + e.getMessage());
                    }
                    continue;
                }

                if (input.startsWith("/download ")) {
                    String filename = input.substring(10).trim();
                    try {
                        Path target = Paths.get(filename);
                        fileService.download(filename, target);
                        lineReader.printAbove("[System] Downloaded: " + target.toAbsolutePath());
                    } catch (Exception e) {
                        lineReader.printAbove("[Error] Download failed: " + e.getMessage());
                    }
                    continue;
                }

                if (!input.isBlank()) {
                    wsClient.sendChatMessage(input, room.getId());
                }
            }

        } catch (Exception e) {
            System.err.println("[Error] Could not connect to chat: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
