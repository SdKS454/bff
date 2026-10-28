package ru.kicksharing.documents.client;

import com.fasterxml.jackson.databind.JsonNode;
import ru.kicksharing.documents.config.AppConfig;
import ru.kicksharing.documents.exception.ApiException;
import ru.kicksharing.documents.util.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ServiceHttp {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();

    private ServiceHttp() {
    }

    public static HttpResponse<String> get(String baseKey, String path, String jwt) {
        try {
            String base = AppConfig.get(baseKey);
            URI uri = URI.create(base.replaceAll("/+$", "") + path);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException();
            return CLIENT.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + jwt).header("Accept", "application/json")
                    .GET().build(), HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.serviceUnavailable("Запрос к сервису прерван");
        } catch (IOException e) {
            throw ApiException.serviceUnavailable("Зависимый сервис временно недоступен");
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.serviceUnavailable("Некорректная настройка адреса зависимого сервиса");
        }
    }

    public static JsonNode json(String baseKey, String path, String jwt) {
        HttpResponse<String> response = get(baseKey, path, jwt);
        int status = response.statusCode();
        if (status == 401 || status == 403 || status == 404 || status == 409)
            throw new ApiException(status, "Зависимый сервис отклонил запрос");
        if (status == 429 || status >= 500)
            throw ApiException.serviceUnavailable("Зависимый сервис временно недоступен");
        if (status != 200) throw badResponse();
        String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim();
        if (!type.equalsIgnoreCase("application/json")) throw badResponse();
        try {
            JsonNode value = JsonUtil.getMapper().readTree(response.body());
            if (value == null || value.isNull()) throw badResponse();
            return value;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw badResponse();
        }
    }

    public static <T> T object(JsonNode value, Class<T> type) {
        if (!value.isObject()) throw badResponse();
        try {
            return JsonUtil.getMapper().treeToValue(value, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            throw badResponse();
        }
    }

    public static boolean sameId(String actual, String expected) {
        try {
            return actual != null && actual.matches("[0-9a-fA-F-]{36}")
                    && java.util.UUID.fromString(actual).equals(java.util.UUID.fromString(expected));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static ApiException badResponse() {
        return new ApiException(502, "Некорректный ответ зависимого сервиса");
    }
}
