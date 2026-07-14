package com.petstore.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.type.TypeReference;
import com.petstore.cli.auth.CredentialsStore;
import com.petstore.cli.generated.ApiClient;
import com.petstore.cli.generated.api.CliApi;

/**
 * Shared configuration + factory used by the generated command classes.
 *
 * The host and bearer token are captured once at {@code login} and stored in
 * {@link CredentialsStore}; ordinary commands take neither as a flag. The base URL resolves as:
 * {@code PETSTORE_BASE_URL} env &gt; the stored host &gt; {@code http://localhost}; the bearer
 * token (the service ticket) comes from the store and is sent as {@code Authorization: Bearer}.
 */
public final class CliContext {

    private CliContext() {
    }

    public static ApiClient apiClient() {
        final String bearer = CredentialsStore.token();

        ApiClient client = new ApiClient();
        client.updateBaseUri(baseUrl());
        client.setRequestInterceptor(builder -> {
            if (bearer != null && !bearer.isBlank()) {
                builder.header("Authorization", "Bearer " + bearer);
            }
        });
        return client;
    }

    /** Whether a cached token is present; used by generated commands to gate protected calls. */
    public static boolean hasBearerToken() {
        return CredentialsStore.token() != null;
    }

    public static CliApi api() {
        return new CliApi(apiClient());
    }

    /** Pretty-prints a response using the client's configured Jackson mapper. */
    public static String render(Object value) {
        try {
            return apiClient().getObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
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

    /** The resolved base URL for ordinary commands: env &gt; stored host &gt; localhost. */
    public static String baseUrl() {
        return resolveBaseUrl(null);
    }

    /**
     * The base URL with an explicit override taking top precedence -- used by {@code login},
     * the only command that accepts {@code --base-url}. Precedence: override &gt;
     * {@code PETSTORE_BASE_URL} &gt; stored host &gt; {@code http://localhost}.
     */
    public static String resolveBaseUrl(String override) {
        return firstNonBlank(override, System.getenv("PETSTORE_BASE_URL"),
                CredentialsStore.host(), "http://localhost");
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
