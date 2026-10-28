package ru.itis.security;

import ru.itis.exception.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;




public final class UserTokenValidator {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final URI endpoint;

    public UserTokenValidator() {
        this(System.getenv().getOrDefault("USER_BASE_URL", "http://localhost:8081"));
    }

    UserTokenValidator(String baseUrl) {
        URI base = URI.create(baseUrl);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null)
            throw new IllegalArgumentException("USER_BASE_URL must be an HTTP(S) base URL");
        endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/api/users/validate");
    }

    public void validate(String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(2))
                    .header("Authorization", "Bearer " + token).GET().build();
            int status = CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 204) return;
            if (status == 401) throw new ApiException(401, "Требуется авторизация");

            throw new ApiException(503, "Сервис авторизации временно недоступен");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "Сервис авторизации временно недоступен");
        } catch (IOException e) {
            throw new ApiException(503, "Сервис авторизации временно недоступен");
        } catch (IllegalArgumentException e) {
            throw new ApiException(401, "Требуется авторизация");
        }
    }
}
