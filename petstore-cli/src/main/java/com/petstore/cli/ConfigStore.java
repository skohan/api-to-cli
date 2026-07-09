package com.petstore.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Persists CLI configuration (host URL, username, api key) at {@code ~/.petstore-cli/.config}
 * so subsequent invocations don't need the values repeated. Written by the {@code login}
 * command; read by {@link CliContext} as the fallback below flags and environment variables.
 *
 * The password is deliberately NOT stored -- the bearer token cached by
 * {@code TokenStore} is the persistent credential.
 */
public final class ConfigStore {

    public static final String KEY_BASE_URL = "base-url";
    public static final String KEY_API_KEY = "api-key";
    public static final String KEY_USERNAME = "username";

    private static final Path CONFIG_FILE =
            Path.of(System.getProperty("user.home"), ".petstore-cli", ".config");

    private ConfigStore() {
    }

    public static String get(String key) {
        return load().getProperty(key);
    }

    /** Merges the given (non-blank) entries into the existing config and saves it. */
    public static void save(Map<String, String> entries) {
        Properties props = load();
        Map<String, String> clean = new LinkedHashMap<>();
        entries.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                clean.put(k, v);
            }
        });
        clean.forEach(props::setProperty);
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                props.store(out, "petstore-cli configuration");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write config at " + CONFIG_FILE, e);
        }
    }

    private static Properties load() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException e) {
                // Unreadable config: behave as if absent.
            }
        }
        return props;
    }

    public static Path location() {
        return CONFIG_FILE;
    }
}
