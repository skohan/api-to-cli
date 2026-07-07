package com.petstore.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.type.TypeReference;
import com.petstore.cli.generated.ApiClient;
import com.petstore.cli.generated.api.CliApi;

/**
 * Shared configuration + factory used by the generated command classes. Base URL and
 * API key come from the root command's global options (see {@link PetstoreCli}), falling
 * back to the {@code PETSTORE_BASE_URL} / {@code PETSTORE_API_KEY} environment variables.
 */
public final class CliContext {

    private static volatile String baseUrl = envOrDefault("PETSTORE_BASE_URL", "http://localhost");
    private static volatile String apiKey = System.getenv("PETSTORE_API_KEY");

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
        if (key != null && !key.isBlank()) {
            client.setRequestInterceptor(builder -> builder.header("api_key", key));
        }
        return client;
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

    public static String baseUrl() {
        return baseUrl;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
