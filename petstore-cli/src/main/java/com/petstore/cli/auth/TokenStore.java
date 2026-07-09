package com.petstore.cli.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists the bearer token (service ticket) obtained by the {@code login} command so that
 * subsequent CLI invocations can authenticate without logging in again. Stored as a single
 * file under {@code ./.petstore-cli/token} (relative to the current working directory).
 */
public final class TokenStore {

    private static final Path TOKEN_FILE =
            Path.of("./.token");

    private TokenStore() {
    }

    public static void save(String token) {
        try {
            Files.createDirectories(TOKEN_FILE.getParent());
            Files.writeString(TOKEN_FILE, token);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store auth token at " + TOKEN_FILE, e);
        }
    }

    public static Optional<String> load() {
        try {
            if (Files.exists(TOKEN_FILE)) {
                String token = Files.readString(TOKEN_FILE).trim();
                if (!token.isEmpty()) {
                    return Optional.of(token);
                }
            }
        } catch (IOException e) {
            // Treat an unreadable token file as "not logged in".
        }
        return Optional.empty();
    }

    public static void clear() {
        try {
            Files.deleteIfExists(TOKEN_FILE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not clear auth token at " + TOKEN_FILE, e);
        }
    }

    public static Path location() {
        return TOKEN_FILE;
    }
}
