package com.example.mono;

import com.example.mono.service.AuthService;

import java.util.Scanner;

public class AuthFlow {

    public record Result(String token, String username) {}

    private final AuthService authService;
    private final Scanner scanner;

    public AuthFlow(AuthService authService, Scanner scanner) {
        this.authService = authService;
        this.scanner = scanner;
    }

    public Result run() {
        while (true) {
            System.out.println("\n1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");
            System.out.print("Choose: ");

            String line = scanner.nextLine().trim();
            switch (line) {
                case "1" -> {
                    Result result = handleLogin();
                    if (result != null) return result;
                }
                case "2" -> handleRegister();
                case "3" -> {
                    System.out.println("Goodbye.");
                    System.exit(0);
                }
                default -> System.out.println("[Error] Invalid choice.");
            }
        }
    }

    private Result handleLogin() {
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        if (username.isBlank() || password.isBlank()) {
            System.out.println("[Error] Credentials cannot be empty.");
            return null;
        }

        try {
            String token = authService.login(username, password);
            if (token == null) {
                System.out.println("[Error] Invalid username or password.");
                return null;
            }
            return new Result(token, username);
        } catch (Exception e) {
            System.out.println("[Error] " + e.getMessage());
            return null;
        }
    }

    private void handleRegister() {
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        if (username.isBlank() || password.isBlank()) {
            System.out.println("[Error] Credentials cannot be empty.");
            return;
        }

        try {
            String message = authService.register(username, password);
            System.out.println("[OK] " + message + " You can now log in.");
        } catch (Exception e) {
            System.out.println("[Error] " + e.getMessage());
        }
    }
}
