package ru.scooter.user.client;

import com.fasterxml.jackson.databind.JsonNode;
import ru.scooter.user.exception.ApiException;
import ru.scooter.user.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public final class HttpModerationClient implements ModerationClient {
    private final URI baseUrl;
    private final HttpClient http;
    private final Duration timeout;

    public HttpModerationClient(URI baseUrl, Duration timeout) {
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.http =
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    @Override
    public boolean isBlocked(UUID userId, String token) {
        return status(
                send("GET", "/api/moderation/users/" + userId + "/status", null, token),
                userId)
                .get("blocked")
                .booleanValue();
    }

    @Override
    public JsonNode ban(UUID userId, String reason, String token) {
        return status(
                send(
                        "POST",
                        "/api/moderation/users/" + userId + "/ban",
                        Map.of("reason", reason),
                        token),
                userId);
    }

    @Override
    public JsonNode unban(UUID userId, String token) {
        return status(
                send("POST", "/api/moderation/users/" + userId + "/unban", null, token), userId);
    }

    @Override
    public JsonNode blockedUsers(int limit, int offset, String token) {
        JsonNode node =
                send(
                        "GET",
                        "/api/moderation/users?blocked=true&limit=" + limit + "&offset=" + offset,
                        null,
                        token);
        if (!node.isArray()) throw malformed();
        return node;
    }

    @Override
    public JsonNode reasons(String token) {
        JsonNode node = send("GET", "/api/moderation/reasons", null, token);
        if (!node.isArray()) throw malformed();
        return node;
    }

    private JsonNode status(JsonNode node, UUID userId) {
        if (!node.isObject()
                || !node.path("blocked").isBoolean()
                || !userId.toString().equals(node.path("userId").asText())) throw malformed();
        return node;
    }

    private JsonNode send(String method, String path, Object body, String token) {
        try {
            HttpRequest.Builder request =
                    HttpRequest.newBuilder(URI.create(baseUrl.toString() + path))
                            .timeout(timeout)
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer " + token);
            if (body != null)
                request.header("Content-Type", "application/json")
                        .method(
                                method,
                                HttpRequest.BodyPublishers.ofByteArray(
                                        Json.MAPPER.writeValueAsBytes(body)));
            else request.method(method, HttpRequest.BodyPublishers.noBody());
            HttpResponse<String> response =
                    http.send(
                            request.build(),
                            HttpResponse.BodyHandlers.ofString(
                                    java.nio.charset.StandardCharsets.UTF_8));
            int code = response.statusCode();
            if (code >= 500 || code == 429)
                throw new ApiException(503, "Сервис модерации недоступен");
            if (code < 200 || code >= 300) {
                if (code == 400 || code == 401 || code == 403 || code == 409) {
                    String message = "Модерация отклонила запрос";
                    try {
                        JsonNode error = Json.MAPPER.readTree(response.body());
                        if (error != null && error.path("error").isTextual())
                            message = error.get("error").textValue();
                    } catch (IOException ignored) {
                    }
                    throw new ApiException(code, message);
                }
                throw malformed();
            }
            if (response.body().length() > 1_048_576) throw malformed();
            try {
                JsonNode result = Json.MAPPER.readTree(response.body());
                if (result == null) throw malformed();
                return result;
            } catch (IOException e) {
                throw malformed();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "Запрос к модерации прерван", e);
        } catch (IOException e) {
            throw new ApiException(503, "Сервис модерации недоступен", e);
        }
    }

    private ApiException malformed() {
        return new ApiException(502, "Некорректный ответ сервиса модерации");
    }
}
