package bff.service.impl;

import bff.dto.AuthRequest;
import bff.dto.AuthResponse;
import bff.dto.JsonResponse;
import bff.dto.UserDto;
import bff.service.ServiceException;
import bff.service.UserService;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public class UserServiceRestImpl implements UserService {

    private static final String BASE = ServiceUrls.get("user.service.url", "http://localhost:8081");

    private final HttpClient httpClient = HttpClients.shared();

    @Override
    public JsonResponse register(AuthRequest request) {
        return send("POST", URI.create(BASE + "/api/users/register"), null, toJson(request));
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        JsonResponse response = send("POST", URI.create(BASE + "/api/users/login"), null, "{\"login\":" + JsonText.quote(request.login())
                + ",\"password\":" + JsonText.quote(request.password()) + "}");
        if (response.status() < 200 || response.status() >= 300) {
            throw failure(response);
        }
        return parseAuth(response.body());
    }

    @Override
    public JsonResponse logout(String jwt) {
        return send("POST", URI.create(BASE + "/api/users/logout"), jwt, "{}");
    }

    @Override
    public JsonResponse profile(String jwt) {
        return send("GET", URI.create(BASE + "/api/users/me"), jwt, null);
    }

    @Override
    public UserDto currentUser(String jwt) {
        JsonResponse response = profile(jwt);
        if (response.status() < 200 || response.status() >= 300) {
            throw failure(response);
        }
        UserDto user;
        try {
            user = parseUser(response.body());
        } catch (RuntimeException e) {
            throw new ServiceException(502, "Неожиданный формат ответа пользователя", e);
        }
        if (user.id() == null) {
            throw new ServiceException(502, "Неожиданный формат ответа пользователя");
        }
        return user;
    }

    @Override
    public JsonResponse exists(String login) {
        String query = URLEncoder.encode(login, StandardCharsets.UTF_8);
        return send("GET", URI.create(BASE + "/api/users/exists?login=" + query), null, null);
    }

    @Override
    public boolean validate(String jwt) {
        JsonResponse response = send("GET", URI.create(BASE + "/api/users/validate"), jwt, null);
        if (response.status() >= 500) throw new ServiceException(503, "Сервис проверки токена недоступен");
        return response.status() >= 200 && response.status() < 300;
    }

    @Override
    public boolean isBlocked(UUID userId, String jwt) {
        JsonResponse response = send(
                "GET", URI.create(BASE + "/api/users/" + userId + "/blocked"), jwt, null);
        if (response.status() >= 500) throw new ServiceException(503, "Сервис проверки блокировки недоступен");
        if (response.status() < 200 || response.status() >= 300) throw failure(response);
        String body = response.body() == null ? "" : response.body().trim();
        if ("true".equals(body)) return true;
        if ("false".equals(body)) return false;
        try {
            String value = JsonText.raw(body, "blocked");
            if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException();
            return "true".equals(value);
        } catch (RuntimeException e) {
            throw new ServiceException(502, "Неожиданный формат статуса блокировки", e);
        }
    }

    private String toJson(AuthRequest request) {
        return "{\"login\":" + JsonText.quote(request.login())
                + ",\"password\":" + JsonText.quote(request.password())
                + ",\"fullName\":" + JsonText.quote(request.fullName())
                + ",\"birthDate\":" + JsonText.quote(request.birthDate())
                + ",\"avatarUrl\":" + JsonText.quote(request.avatarUrl()) + "}";
    }

    private AuthResponse parseAuth(String json) {
        try {
            String token = JsonText.string(json, "accessToken");
            if (token == null || token.isBlank()) {
                throw new ServiceException(502, "Неожиданный формат ответа авторизации");
            }
            Long expires = JsonText.longOrNull(json, "expiresInSeconds");
            if (expires == null || expires <= 0 || expires > Integer.MAX_VALUE)
                throw new ServiceException(502, "Некорректное время жизни токена");
            String userJson = JsonText.raw(json, "user");
            UserDto user = userJson == null || "null".equals(userJson) ? null : parseUser(userJson);
            if (user == null || user.id() == null)
                throw new ServiceException(502, "Нет UUID пользователя в ответе авторизации");
            if (user.deleted()) throw new ServiceException(403, "Пользователь удалён");
            return new AuthResponse(token, expires, user);
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException(502, "Неожиданный формат ответа авторизации", e);
        }
    }

    private UserDto parseUser(String json) {
        if (json == null || !json.trim().startsWith("{")) {
            throw new IllegalStateException("Неожиданный формат ответа пользователя");
        }
        return new UserDto(
                JsonText.uuidOrNull(json, "id"),
                JsonText.string(json, "login"),
                JsonText.string(json, "role"),
                JsonText.string(json, "fullName"),
                JsonText.string(json, "birthDate"),
                JsonText.string(json, "avatarUrl"),
                JsonText.bool(json, "deleted"),
                JsonText.bool(json, "blocked"));
    }

    private ServiceException failure(JsonResponse response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            body = "HTTP " + response.status();
        }
        return new ServiceException(response.status(), body);
    }

    private JsonResponse send(String method, URI uri, String jwt, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10));
        if (jwt != null && !jwt.isBlank()) {
            builder.header("Authorization", "Bearer " + jwt);
        }
        if (json != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            return new JsonResponse(response.statusCode(), body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(503, "Запрос прерван", e);
        } catch (IOException e) {
            throw new ServiceException(503, "Не удалось связаться с сервисом пользователей", e);
        }
    }
}
