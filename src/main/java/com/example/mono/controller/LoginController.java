package com.example.mono.controller;

import com.example.mono.util.AuthResponse;
import com.example.mono.model.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorMessage;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML
    protected void onLoginButtonClick() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorMessage.setText("Please enter both username and password.");
            return;
        }

        errorMessage.setTextFill(Color.WHITE);
        errorMessage.setText("Connecting to server...");

        try{
            LoginRequest loginRequest = new LoginRequest(username, password);
            String json = objectMapper.writeValueAsString(loginRequest);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:8080/api/auth/login"))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json")
                    .build();


            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            if (response.statusCode() == 200 || response.statusCode() == 201) {
                                try {
                                    AuthResponse authResponse = objectMapper.readValue(response.body(), AuthResponse.class);
                                    System.out.println("Success: " + authResponse.getToken() + " is your token.");
                                    errorMessage.setTextFill(Color.LIGHTGREEN);
                                    errorMessage.setText("Login successful!");
                                } catch (Exception e) {
                                    errorMessage.setTextFill(Color.RED);
                                    errorMessage.setText("Error during parsing.");
                                }
                            } else {
                                errorMessage.setTextFill(Color.RED);
                                errorMessage.setText("Unexpected response processing error" + response.statusCode());
                            }
                        });
                    })
            .exceptionally((exception) -> {
                Platform.runLater(() -> {
                    errorMessage.setTextFill(Color.RED);
                    errorMessage.setText("Unexpected aplication error");
                });
                return null;
            });

        }catch (Exception e) {
            errorMessage.setTextFill(Color.RED);
            errorMessage.setText("Unexpected aplication error");
            e.printStackTrace();
        }


    }
}
