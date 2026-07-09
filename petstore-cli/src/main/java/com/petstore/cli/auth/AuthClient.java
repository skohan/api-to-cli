package com.petstore.cli.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Performs the two-step SSO login against endpoints that are deliberately NOT part of the
 * OpenAPI document (so they are not generated as commands):
 *
 * <ol>
 *   <li>POST {base}/sso/caf/authenticate/DB with {"username","password"} JSON -&gt; TGT
 *       (ticket-granting ticket) in the response body.</li>
 *   <li>POST {base}/sso/caf/authenticate/serviceticket with the TGT as the request body
 *       -&gt; bearer token (service ticket) in the response body.</li>
 * </ol>
 *
 * The returned bearer token is what protected API commands send as {@code Authorization:
 * Bearer <token>}.
 */
public final class AuthClient {

    private static final String TGT_PATH = "/sso/caf/authenticate/DB";
    private static final String SERVICE_TICKET_PATH = "/sso/caf/authenticate/serviceticket";

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newHttpClient();
    }

    /** Runs both steps and returns the bearer token, or throws with a readable message. */
    public String login(String username, String password) {
        String tgt = requestTgt(username, password);
        return requestServiceTicket(tgt);
    }

    private String requestTgt(String username, String password) {
        String json;
        try {
            json = mapper.writeValueAsString(Map.of("username", username, "password", password));
        } catch (Exception e) {
            throw new IllegalStateException("Could not build authentication request: " + e.getMessage(), e);
        }
        HttpResponse<String> response = send(TGT_PATH, json, "application/json");
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "Authentication failed at " + TGT_PATH + " (HTTP " + response.statusCode() + "): "
                            + response.body());
        }
        String tgt = response.body().trim();
        if (tgt.isEmpty()) {
            throw new IllegalStateException("Authentication succeeded but no TGT was returned.");
        }
        return tgt;
    }

    private String requestServiceTicket(String tgt) {
        HttpResponse<String> response = send(SERVICE_TICKET_PATH, tgt, "text/plain");
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "Service-ticket exchange failed at " + SERVICE_TICKET_PATH + " (HTTP "
                            + response.statusCode() + "): " + response.body());
        }
        // The endpoint returns the ticket as a JSON string, i.e. wrapped in double
        // quotes. Strip them so the raw token is what gets cached and sent as
        // "Authorization: Bearer <token>".
        String token = unquote(response.body().trim());
        if (token.isEmpty()) {
            throw new IllegalStateException("Service-ticket exchange succeeded but no token was returned.");
        }
        return token;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private HttpResponse<String> send(String path, String body, String contentType) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", contentType)
                .header("Accept", "*/*")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not reach " + baseUrl + path + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Login interrupted", e);
        }
    }
}
