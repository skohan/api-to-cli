package com.petstore.cli.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Single-host CLI configuration, stored as a hidden {@code .config} file in the current working
 * directory. It holds the host (base URL), the bearer token (the service ticket obtained at
 * {@code login}), and the username, so later commands need no flags.
 *
 * The bearer token (service ticket) is the only credential: it is what protected commands send
 * as {@code Authorization: Bearer}. There is no separate api key.
 *
 * The format is a plain {@code key=value} properties file, not JSON:
 * <pre>
 * host=https://prod.example.com
 * token=...
 * username=alice
 * </pre>
 *
 * Multi-host support was intentionally dropped for now; exactly one current host is stored. The
 * file is hidden via the leading dot (Linux/macOS) and the DOS hidden attribute (Windows).
 */
public final class CredentialsStore {

    private static final Path FILE = Path.of("./.config");

    private static final String KEY_HOST = "host";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";

    private CredentialsStore() {
    }

    /** The stored host (base URL), or null if not set. */
    public static String host() {
        return trimToNull(load().getProperty(KEY_HOST));
    }

    /** The stored bearer token (service ticket), or null if not logged in. */
    public static String token() {
        return trimToNull(load().getProperty(KEY_TOKEN));
    }

    /** The stored username, or null if not set. */
    public static String username() {
        return trimToNull(load().getProperty(KEY_USERNAME));
    }

    /** Persists the host/token/username captured at login. */
    public static void save(String host, String token, String username) {
        Properties props = new Properties();
        putIfPresent(props, KEY_HOST, host);
        putIfPresent(props, KEY_TOKEN, token);
        putIfPresent(props, KEY_USERNAME, username);
        store(props);
    }

    /** Deletes the config file entirely. No-op if it is absent. */
    public static void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not remove " + FILE, e);
        }
    }

    public static Path location() {
        return FILE;
    }

    private static Properties load() {
        Properties props = new Properties();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                props.load(in);
            } catch (IOException e) {
                // Unreadable/corrupt file: behave as if empty rather than crash every command.
            }
        }
        return props;
    }

    private static void store(Properties props) {
        try {
            Path parent = FILE.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // Recreate rather than truncate: rewriting a file that already carries the Windows
            // hidden attribute in place throws AccessDeniedException on that platform.
            Files.deleteIfExists(FILE);
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "petstore-cli configuration");
            }
            hide(FILE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write config at " + FILE, e);
        }
    }

    private static void hide(Path path) {
        try {
            Object hidden = Files.getAttribute(path, "dos:hidden");
            if (Boolean.FALSE.equals(hidden)) {
                Files.setAttribute(path, "dos:hidden", Boolean.TRUE);
            }
        } catch (UnsupportedOperationException | IOException | IllegalArgumentException ignored) {
            // Non-DOS filesystem (Linux/macOS): the leading dot already hides it.
        }
    }

    private static void putIfPresent(Properties props, String key, String value) {
        if (value != null && !value.isBlank()) {
            props.setProperty(key, value);
        }
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
