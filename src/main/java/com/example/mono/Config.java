package com.example.mono;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties props = new Properties();

    static {
        String profile = System.getenv("APP_PROFILE");
        if (profile == null) profile = "prod";

        String file = "config-" + profile + ".properties";
        try (InputStream in = Config.class.getResourceAsStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Nie można załadować konfiguracji: " + file);
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}