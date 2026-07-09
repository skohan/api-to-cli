package com.petstore.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.type.TypeReference;
import com.petstore.cli.auth.TokenStore;
import com.petstore.cli.generated.ApiClient;
import com.petstore.cli.generated.api.CliApi;

/**
 * Shared configuration + factory used by the generated command classes. Base URL and API
 * key resolve with the precedence: command-line flag &gt; environment variable &gt;
 * {@code ~/.petstore-cli/.config} (written by the {@code login} command) &gt; default.
 * Subcommands never read the flags directly -- they call {@link #apiClient()} /
 * {@link #api()}, which use the resolved values.
 */
public final class CliContext {

    private static volatile String baseUrl = firstNonBlank(
            System.getenv("PETSTORE_BASE_URL"),
            ConfigStore.get(ConfigStore.KEY_BASE_URL),
            "http://localhost");
    private static volatile String apiKey = firstNonBlank(
            System.getenv("PETSTORE_API_KEY"),
            ConfigStore.get(ConfigStore.KEY_API_KEY),
            null);

    private CliContext() {
    }

    /** Applies overrides supplied on the command line; blank/null values are ignored. */
    public static void configure(String baseUrlOverride, String apiKeyOverride) {
        if (baseUrlOverride != null && !baseUrlOverride.isBlank()) {
            baseUrl = baseUrlOverride;
        }
        if (apiKeyOverride != null && !apiKeyOverride.isBlank()) {
            apiKey = apiKeyOverride;
        }
    }

    public static ApiClient apiClient() {
        ApiClient client = new ApiClient();
        client.updateBaseUri(baseUrl);
        final String key = apiKey;
        final String bearer = TokenStore.load().orElse(null);
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

    /** Whether a cached bearer token exists; used by generated commands to gate protected calls. */
    public static boolean hasBearerToken() {
        return TokenStore.load().isPresent();
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

    public static String baseUrl() {
        return baseUrl;
    }

    public static String apiKey() {
        return apiKey;
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
