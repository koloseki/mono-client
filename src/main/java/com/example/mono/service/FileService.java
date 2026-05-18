package com.example.mono.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class FileService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;

    public FileService(String baseUrl, String token) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public Map<String, String> upload(Path filePath) throws Exception {
        String boundary = UUID.randomUUID().toString();
        byte[] fileBytes = Files.readAllBytes(filePath);
        String filename = filePath.getFileName().toString();

        String bodyStart = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String bodyEnd = "\r\n--" + boundary + "--\r\n";

        byte[] start = bodyStart.getBytes();
        byte[] end = bodyEnd.getBytes();
        byte[] body = new byte[start.length + fileBytes.length + end.length];
        System.arraycopy(start, 0, body, 0, start.length);
        System.arraycopy(fileBytes, 0, body, start.length, fileBytes.length);
        System.arraycopy(end, 0, body, start.length + fileBytes.length, end.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/files/upload"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), Map.class);
        } else {
            throw new Exception("Upload failed: " + response.statusCode() + " " + response.body());
        }
    }

    public void download(String filename, Path targetPath) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/files/" + filename))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            Files.write(targetPath, response.body());
        } else {
            throw new Exception("Download failed: " + response.statusCode());
        }
    }
}
