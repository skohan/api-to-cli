package com.petstore.cli.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Unified, per-host credential + config store -- the CLI's equivalent of kubeconfig.
 * A single JSON file holds, for each host (base URL), its bearer token, username and api
 * key, plus a {@code currentHost} pointer (like kubectl's current-context). Logging in to
 * a host stores its token there and makes it current; switching hosts therefore never
 * clobbers another host's token.
 *
 * <pre>
 * {
 *   "currentHost": "https://prod.example.com",
 *   "hosts": {
 *     "https://prod.example.com":  { "token": "...", "username": "alice" },
 *     "http://localhost:8080":     { "token": "...", "username": "dev" }
 *   }
 * }
 * </pre>
 *
 * The password is never stored -- the cached bearer token is the durable credential.
 */
public final class CredentialsStore {

    /** Credentials + config for a single host. */
    @JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
            setterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class HostCredentials {
        public String token;
        public String username;
        public String apiKey;
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
            setterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static final class Root {
        public String currentHost;
        public Map<String, HostCredentials> hosts = new LinkedHashMap<>();
    }

    private static final Path FILE = Path.of("./.petstore-cli.json");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private CredentialsStore() {
    }

    /** The host most recently logged in to (or selected), or null if none. */
    public static String currentHost() {
        return load().currentHost;
    }

    /** Credentials for a host, or null if that host is unknown. */
    public static HostCredentials get(String host) {
        return host == null ? null : load().hosts.get(host);
    }

    /** All known hosts, for display. */
    public static Map<String, HostCredentials> hosts() {
        return load().hosts;
    }

    /** Stores the token/username/api-key under the host and makes it the current host. */
    public static void saveLogin(String host, String token, String username, String apiKey) {
        Root root = load();
        HostCredentials creds = root.hosts.computeIfAbsent(host, k -> new HostCredentials());
        creds.token = token;
        if (username != null && !username.isBlank()) {
            creds.username = username;
        }
        creds.apiKey = (apiKey == null || apiKey.isBlank()) ? null : apiKey;
        root.currentHost = host;
        store(root);
    }

    /** Removes just the token for a host (keeps username for easy re-login). No-op if unknown. */
    public static void clearToken(String host) {
        Root root = load();
        HostCredentials creds = root.hosts.get(host);
        if (creds != null && creds.token != null) {
            creds.token = null;
            store(root);
        }
    }

    public static Path location() {
        return FILE;
    }

    private static Root load() {
        if (Files.exists(FILE)) {
            try {
                return MAPPER.readValue(FILE.toFile(), Root.class);
            } catch (IOException e) {
                // Unreadable/corrupt file: behave as if empty rather than crash every command.
            }
        }
        return new Root();
    }

    private static void store(Root root) {
        try {
            Path parent = FILE.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(FILE.toFile(), root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write credentials at " + FILE, e);
        }
    }
}
