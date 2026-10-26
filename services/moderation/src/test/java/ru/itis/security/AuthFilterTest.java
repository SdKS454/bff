package ru.itis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthFilterTest {

    private static final String STATUS_PATH = "/users/%s/status";

    private final AuthFilter filter = new AuthFilter(token -> {});
    private final UUID userId = UUID.randomUUID();

    private HttpServletResponse resp;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        chain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Нет заголовка Authorization -> 401, цепочка не вызывается")
    void noHeader_401() throws Exception {
        HttpServletRequest req = request("GET", String.format(STATUS_PATH, userId), null);

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Схема не Bearer -> 401")
    void notBearer_401() throws Exception {
        HttpServletRequest req = request("GET", String.format(STATUS_PATH, userId), "Basic abc123");

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("\"Bearer \" с пустым токеном -> 401")
    void emptyToken_401() throws Exception {
        HttpServletRequest req = request("GET", String.format(STATUS_PATH, userId), "Bearer ");

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Мусор вместо JWT -> 401")
    void garbageToken_401() throws Exception {
        HttpServletRequest req = request("GET", String.format(STATUS_PATH, userId), "Bearer garbage");

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("В payload нет userId -> 401")
    void noUserIdInPayload_401() throws Exception {
        String jwt = JwtUtilTest.token("{\"role\":\"ADMIN\"}");
        HttpServletRequest req = request("GET", String.format(STATUS_PATH, userId), "Bearer " + jwt);

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Токен просрочен (exp в прошлом) -> 401")
    void expiredToken_401() throws Exception {
        long past = System.currentTimeMillis() / 1000 - 60;
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\",\"role\":\"ADMIN\",\"exp\":" + past + "}");
        HttpServletRequest req = request("POST", "/users/" + userId + "/ban", "Bearer " + jwt);

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Обычный пользователь пытается забанить -> 403")
    void nonAdminBan_403() throws Exception {
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\",\"role\":\"USER\"}");
        HttpServletRequest req = request("POST", "/users/" + userId + "/ban", "Bearer " + jwt);

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Обычный пользователь запрашивает список заблокированных -> 403")
    void nonAdminList_403() throws Exception {
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\",\"role\":\"USER\"}");
        HttpServletRequest req = request("GET", "/users", "Bearer " + jwt);

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Токен без роли не даёт доступа к админским эндпоинтам -> 403")
    void noRoleAdminEndpoint_403() throws Exception {
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\"}");
        HttpServletRequest req = request("GET", "/reasons", "Bearer " + jwt);

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("POST на путь /status не считается статус-запросом -> 403 для не-админа")
    void nonAdminPostToStatusPath_403() throws Exception {
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\",\"role\":\"USER\"}");
        HttpServletRequest req = request("POST", String.format(STATUS_PATH, userId), "Bearer " + jwt);

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Обычный пользователь может запросить статус -> цепочка вызвана, атрибуты выставлены")
    void nonAdminStatus_passes() throws Exception {
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\",\"role\":\"USER\"}");
        HttpServletRequest req = request("GET", String.format(STATUS_PATH, userId), "Bearer " + jwt);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(req).setAttribute(AuthFilter.ATTR_USER_ID, userId.toString());
        verify(req).setAttribute(AuthFilter.ATTR_ROLE, "USER");
        verify(req).setAttribute(AuthFilter.ATTR_TOKEN, jwt);
        verify(resp, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Админ может банить -> цепочка вызвана")
    void adminBan_passes() throws Exception {
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\",\"role\":\"ADMIN\"}");
        HttpServletRequest req = request("POST", "/users/" + userId + "/ban", "Bearer " + jwt);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(req).setAttribute(AuthFilter.ATTR_ROLE, "ADMIN");
    }

    @Test
    @DisplayName("Токен с exp в будущем принимается")
    void futureExp_passes() throws Exception {
        long future = System.currentTimeMillis() / 1000 + 3600;
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\",\"role\":\"ADMIN\",\"exp\":" + future + "}");
        HttpServletRequest req = request("GET", "/reasons", "Bearer " + jwt);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void revokedOrForgedTokenCannotReachServlet() throws Exception {
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\",\"role\":\"ADMIN\"}");
        AuthFilter rejecting = new AuthFilter(token -> {
            throw new ru.itis.exception.ApiException(401, "revoked");
        });
        rejecting.doFilter(request("POST", "/users/" + userId + "/ban", "Bearer " + jwt), resp, chain);
        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void unavailableUserFailsClosedWithoutCallingServlet() throws Exception {
        String jwt = JwtUtilTest.token("{\"userId\":\"" + userId + "\",\"role\":\"ADMIN\"}");
        AuthFilter rejecting = new AuthFilter(token -> {
            throw new ru.itis.exception.ApiException(503, "unavailable");
        });
        rejecting.doFilter(request("POST", "/users/" + userId + "/ban", "Bearer " + jwt), resp, chain);
        verify(resp).setStatus(503);
        verify(chain, never()).doFilter(any(), any());
    }

    private HttpServletRequest request(String method, String pathInfo, String authHeader) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getServletPath()).thenReturn("/api/moderation");
        when(req.getPathInfo()).thenReturn(pathInfo);
        when(req.getHeader("Authorization")).thenReturn(authHeader);
        return req;
    }
}
