package ru.itis.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.itis.util.ApiException;
import ru.itis.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@WebFilter("/api/*")
public class AuthFilter implements Filter {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        var req = (HttpServletRequest) request;
        var resp = (HttpServletResponse) response;
        if (ru.itis.util.BrowserNavigation.reject(req, resp)) return;
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        try {
            String path = req.getRequestURI().substring(req.getContextPath().length());
            boolean internal = path.matches("/api/scooters/[^/]+/(reserve|release|position)");
            if (internal) {
                String expected = System.getenv("SERVICE_API_KEY"), actual = req.getHeader("X-Service-Key");
                if (expected == null || expected.isBlank()) throw new ApiException(503, "Не настроен ключ интеграции");
                if (actual == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)))
                    throw new ApiException(403, "Недостаточно прав");
            } else {
                String auth = req.getHeader("Authorization");
                if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7) || auth.substring(7).isBlank())
                    throw new ApiException(401, "Нужен Bearer JWT");
                String base = System.getenv().getOrDefault("USER_SERVICE_URL", "http://localhost:8081").replaceAll("/+$", "");
                try {
                    int code = CLIENT.send(HttpRequest.newBuilder(URI.create(base + "/api/users/validate")).timeout(Duration.ofSeconds(3)).header("Authorization", auth).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
                    if (code == 401) throw new ApiException(401, "Недействительный JWT");
                    if (code != 204) throw new ApiException(503, "Проверка токена недоступна");
                    if (path.equals("/api/admin/scooters") || path.startsWith("/api/admin/scooters/")) {
                        var me = CLIENT.send(HttpRequest.newBuilder(URI.create(base + "/api/users/me")).timeout(Duration.ofSeconds(3)).header("Authorization", auth).GET().build(), HttpResponse.BodyHandlers.ofString());
                        if (me.statusCode() == 401) throw new ApiException(401, "Недействительный JWT");
                        if (me.statusCode() != 200) throw new ApiException(503, "Проверка роли недоступна");
                        if (!"ADMIN".equals(Json.MAPPER.readTree(me.body()).path("role").asText()))
                            throw new ApiException(403, "Требуется роль ADMIN");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(503, "Запрос прерван");
                } catch (IOException e) {
                    throw new ApiException(503, "User Service недоступен");
                }
            }
            chain.doFilter(req, resp);
        } catch (ApiException e) {
            Json.error(resp, e.status, e.getMessage());
        } catch (RuntimeException e) {
            req.getServletContext().log("Scooter request failed", e);
            Json.error(resp, 500, "Внутренняя ошибка сервиса");
        }
    }
}
