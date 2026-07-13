package com.petstore.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.type.TypeReference;
import com.petstore.cli.auth.CredentialsStore;
import com.petstore.cli.auth.CredentialsStore.HostCredentials;
import com.petstore.cli.generated.ApiClient;
import com.petstore.cli.generated.api.CliApi;
import com.petstore.cli.output.OutputFormat;

/**
 * Shared configuration + factory used by the generated command classes.
 *
 * The base URL resolves as: command-line flag &gt; {@code PETSTORE_BASE_URL} env &gt; the
 * current host in {@link CredentialsStore} (set by {@code login}) &gt; {@code http://localhost}.
 * The api key and bearer token are then looked up for that resolved host, so credentials
 * are always host-scoped -- switching hosts never uses another host's token. Subcommands
 * never read the flags directly; they call {@link #apiClient()} / {@link #api()}.
 *
 * The output format resolves as: command-line flag &gt; {@code PETSTORE_OUTPUT} env &gt;
 * {@link OutputFormat#JSON}.
 */
public final class CliContext {

    private static volatile String flagBaseUrl;
    private static volatile String flagApiKey;
    private static volatile OutputFormat flagFormat;

    private CliContext() {
    }

    /** Records overrides supplied on the command line; blank/null values are ignored. */
    public static void configure(String baseUrlOverride, String apiKeyOverride) {
        configure(baseUrlOverride, apiKeyOverride, null);
    }

    /** Records overrides supplied on the command line; blank/null values are ignored. */
    public static void configure(String baseUrlOverride, String apiKeyOverride, OutputFormat formatOverride) {
        if (baseUrlOverride != null && !baseUrlOverride.isBlank()) {
            flagBaseUrl = baseUrlOverride;
        }
        if (apiKeyOverride != null && !apiKeyOverride.isBlank()) {
            flagApiKey = apiKeyOverride;
        }
        if (formatOverride != null) {
            flagFormat = formatOverride;
        }
    }

    public static ApiClient apiClient() {
        String host = baseUrl();
        HostCredentials creds = CredentialsStore.get(host);
        final String key = firstNonBlank(flagApiKey, System.getenv("PETSTORE_API_KEY"),
                creds == null ? null : creds.apiKey);
        final String bearer = creds == null ? null : creds.token;

        ApiClient client = new ApiClient();
        client.updateBaseUri(host);
        client.setRequestInterceptor(builder -> {
            if (key != null && !key.isBlank()) {
                builder.header("api_key", key);
            }
            if (bearer != null && !bearer.isBlank()) {
                builder.header("Authorization", "Bearer " + bearer);
            }
        });
        return client;
    }

    /** Whether the current host has a cached token; used by generated commands to gate protected calls. */
    public static boolean hasBearerToken() {
        HostCredentials creds = CredentialsStore.get(baseUrl());
        return creds != null && creds.token != null && !creds.token.isBlank();
    }

    public static CliApi api() {
        return new CliApi(apiClient());
    }

    /** Renders a response in the resolved {@link #outputFormat()} (pretty JSON or a table). */
    public static String render(Object value) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = apiClient().getObjectMapper();
            return outputFormat().format(mapper.valueToTree(value), mapper);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public static OutputFormat outputFormat() {
        if (flagFormat != null) {
            return flagFormat;
        }
        String env = System.getenv("PETSTORE_OUTPUT");
        if (env != null && !env.isBlank()) {
            try {
                return OutputFormat.valueOf(env.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return OutputFormat.JSON;
    }

    /**
     * Parses a request-body option into its model type. The raw value is treated as JSON,
     * unless it starts with '@', in which case the remainder is a path to a JSON file.
     * Returns null when nothing was supplied (an omitted optional body).
     */
    public static <T> T parseJson(String raw, TypeReference<T> type) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String content = raw.startsWith("@")
                    ? Files.readString(Path.of(raw.substring(1)))
                    : raw;
            return apiClient().getObjectMapper().readValue(content, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read JSON input: " + e.getMessage(), e);
        }
    }

    /**
     * Assembles a model from a map of its (flattened) fields, delegating type coercion --
     * including String-to-enum via each enum's {@code @JsonCreator} -- to Jackson.
     */
    public static <T> T convert(Object fields, TypeReference<T> type) {
        return apiClient().getObjectMapper().convertValue(fields, type);
    }

    /**
     * Serializes assembled fields to compact JSON -- used for model-typed multipart parts,
     * which the generated client transmits as a text part exactly as given.
     */
    public static String toJson(Object value) {
        try {
            return apiClient().getObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Places {@code value} into a (possibly nested) map following a dotted path, creating
     * intermediate maps as needed. E.g. {@code putPath(m, "customer.address.city", "NYC")}
     * yields {@code {"customer":{"address":{"city":"NYC"}}}}. The assembled tree is handed
     * to {@link #convert} so Jackson can bind it to the request-body model.
     */
    @SuppressWarnings("unchecked")
    public static void putPath(java.util.Map<String, Object> root, String dottedPath, Object value) {
        String[] parts = dottedPath.split("\\.");
        java.util.Map<String, Object> node = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = node.get(parts[i]);
            if (!(child instanceof java.util.Map)) {
                child = new java.util.LinkedHashMap<String, Object>();
                node.put(parts[i], child);
            }
            node = (java.util.Map<String, Object>) child;
        }
        node.put(parts[parts.length - 1], value);
    }

    /**
     * Moves a file returned by the generated client (a temp file named from the server's
     * Content-Disposition header, when present) to its final destination. {@code output}
     * may be null (current directory, keeping the server-suggested name), an existing
     * directory (file placed inside it), or a target file path (parents created).
     */
    public static java.io.File saveDownload(java.io.File downloaded, java.io.File output) {
        if (downloaded == null) {
            throw new IllegalStateException("The server returned no file content.");
        }
        try {
            Path target;
            if (output == null) {
                target = Path.of(downloaded.getName());
            } else if (output.isDirectory()) {
                target = output.toPath().resolve(downloaded.getName());
            } else {
                target = output.toPath();
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
            }
            Files.move(downloaded.toPath(), target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return target.toFile();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Could not save downloaded file: " + e.getMessage(), e);
        }
    }

    public static String baseUrl() {
        return firstNonBlank(flagBaseUrl, System.getenv("PETSTORE_BASE_URL"),
                CredentialsStore.currentHost(), "http://localhost");
    }

    public static String apiKey() {
        HostCredentials creds = CredentialsStore.get(baseUrl());
        return firstNonBlank(flagApiKey, System.getenv("PETSTORE_API_KEY"),
                creds == null ? null : creds.apiKey);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
