package ru.itis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.itis.entity.BlockStatus;
import ru.itis.entity.Reason;
import ru.itis.entity.enums.BlockReason;
import ru.itis.exception.ValidationException;
import ru.itis.security.AuthFilter;
import ru.itis.service.ModerationService;
import ru.itis.util.Util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ModerationServletTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    private ModerationService moderationService;
    private ModerationServlet servlet;
    private HttpServletResponse resp;
    private StringWriter out;

    @BeforeEach
    void setUp() throws Exception {
        moderationService = mock(ModerationService.class);
        servlet = new ModerationServlet();
        Field field = ModerationServlet.class.getDeclaredField("moderationService");
        field.setAccessible(true);
        field.set(servlet, moderationService);

        resp = mock(HttpServletResponse.class);
        out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
    }

    @Test
    @DisplayName("Неизвестный путь -> 404")
    void unknownPath_404() throws Exception {
        servlet.service(request("GET", "/unknown", "ADMIN", userId), resp);

        verify(resp).setStatus(404);
    }

    @Test
    @DisplayName("Пустой pathInfo -> 404")
    void emptyPath_404() throws Exception {
        servlet.service(request("GET", null, "ADMIN", userId), resp);

        verify(resp).setStatus(404);
    }

    @Test
    @DisplayName("Неверный HTTP-метод -> 405")
    void wrongMethod_405() throws Exception {
        servlet.service(request("GET", "/users/" + userId + "/ban", "ADMIN", userId), resp);

        verify(resp).setStatus(405);
        verifyNoInteractions(moderationService);
    }

    @Test
    @DisplayName("Админ видит статус любого пользователя -> 200")
    void status_admin_200() throws Exception {
        when(moderationService.getStatus(userId)).thenReturn(BlockStatus.notBlocked(userId));

        servlet.service(request("GET", "/users/" + userId + "/status", "ADMIN", otherId), resp);

        verify(resp).setStatus(200);
        JsonNode json = Util.MAPPER.readTree(out.toString());
        assertEquals(userId.toString(), json.get("userId").asText());
        assertFalse(json.get("blocked").asBoolean());
    }

    @Test
    @DisplayName("Пользователь видит собственный статус (userId в токене в другом регистре) -> 200")
    void status_self_200() throws Exception {
        when(moderationService.getStatus(userId)).thenReturn(BlockStatus.notBlocked(userId));
        HttpServletRequest req = request("GET", "/users/" + userId + "/status", "USER", null);
        when(req.getAttribute(AuthFilter.ATTR_USER_ID)).thenReturn(userId.toString().toUpperCase());

        servlet.service(req, resp);

        verify(resp).setStatus(200);
    }

    @Test
    @DisplayName("Пользователь запрашивает статус другого пользователя -> 403")
    void status_otherUser_403() throws Exception {
        servlet.service(request("GET", "/users/" + userId + "/status", "USER", otherId), resp);

        verify(resp).setStatus(403);
        verifyNoInteractions(moderationService);
    }

    @Test
    @DisplayName("Невалидный UUID в пути -> 400")
    void status_invalidUuid_400() throws Exception {
        servlet.service(request("GET", "/users/not-a-uuid/status", "ADMIN", userId), resp);

        verify(resp).setStatus(400);
        verifyNoInteractions(moderationService);
    }

    @Test
    @DisplayName("Статус заблокированного пользователя содержит причину и даты")
    void status_blockedUser_fieldsSerialized() throws Exception {
        Instant at = Instant.parse("2026-01-01T10:00:00.555Z");
        Instant until = Instant.parse("2026-01-04T10:00:00Z");
        when(moderationService.getStatus(userId)).thenReturn(new BlockStatus(
                userId, true, "RIDE_DOUBLE_RIDING", "Езда вдвоем или втроем", at, until));

        servlet.service(request("GET", "/users/" + userId + "/status", "ADMIN", userId), resp);

        JsonNode json = Util.MAPPER.readTree(out.toString());
        assertTrue(json.get("blocked").asBoolean());
        assertEquals("RIDE_DOUBLE_RIDING", json.get("reasonType").asText());
        assertEquals("2026-01-01T10:00:00Z", json.get("blockedAt").asText());
        assertEquals("2026-01-04T10:00:00Z", json.get("blockedUntil").asText());
    }

    @Test
    @DisplayName("GET /reasons -> 200 и список причин")
    void reasons_200() throws Exception {
        when(moderationService.getReasons()).thenReturn(List.of(
                new Reason(BlockReason.RIDE_DOUBLE_RIDING, "Езда вдвоем или втроем", 259200L),
                new Reason(BlockReason.PAYMENT_DEBT, "Неоплаченная задолженность", null)));

        servlet.service(request("GET", "/reasons", "ADMIN", userId), resp);

        verify(resp).setStatus(200);
        JsonNode json = Util.MAPPER.readTree(out.toString());
        assertTrue(json.isArray());
        assertEquals(2, json.size());
        assertEquals("RIDE_DOUBLE_RIDING", json.get(0).get("reasonType").asText());
    }

    @Test
    @DisplayName("GET /users без параметров -> limit=50, offset=0 по умолчанию")
    void users_defaults() throws Exception {
        when(moderationService.listBlocked(anyInt(), anyInt())).thenReturn(List.of());

        servlet.service(request("GET", "/users", "ADMIN", userId), resp);

        verify(moderationService).listBlocked(50, 0);
        verify(resp).setStatus(200);
    }

    @Test
    @DisplayName("GET /users?limit=10&offset=5 передаёт параметры в сервис")
    void users_customParams() throws Exception {
        HttpServletRequest req = request("GET", "/users", "ADMIN", userId);
        when(req.getParameter("limit")).thenReturn("10");
        when(req.getParameter("offset")).thenReturn("5");
        when(moderationService.listBlocked(10, 5)).thenReturn(List.of());

        servlet.service(req, resp);

        verify(moderationService).listBlocked(10, 5);
    }

    @Test
    @DisplayName("GET /users?blocked=false -> 400 (поддерживается только true)")
    void users_blockedFalse_400() throws Exception {
        HttpServletRequest req = request("GET", "/users", "ADMIN", userId);
        when(req.getParameter("blocked")).thenReturn("false");

        servlet.service(req, resp);

        verify(resp).setStatus(400);
        verifyNoInteractions(moderationService);
    }

    @Test
    @DisplayName("GET /users?limit=abc -> 400")
    void users_badLimit_400() throws Exception {
        HttpServletRequest req = request("GET", "/users", "ADMIN", userId);
        when(req.getParameter("limit")).thenReturn("abc");

        servlet.service(req, resp);

        verify(resp).setStatus(400);
    }

    @Test
    @DisplayName("ValidationException из сервиса превращается в 400")
    void users_validationException_400() throws Exception {
        when(moderationService.listBlocked(anyInt(), anyInt()))
                .thenThrow(new ValidationException("Некорректные limit или offset"));

        servlet.service(request("GET", "/users", "ADMIN", userId), resp);

        verify(resp).setStatus(400);
        assertTrue(out.toString().contains("Некорректные limit или offset"));
    }

    @Test
    @DisplayName("POST ban с корректным телом -> 200, сервис вызван с причиной")
    void ban_200() throws Exception {
        BlockStatus blocked = new BlockStatus(userId, true, "RIDE_DANGEROUS", "d",
                Instant.now(), Instant.now().plusSeconds(60));
        when(moderationService.ban(userId, "RIDE_DANGEROUS")).thenReturn(blocked);
        HttpServletRequest req = request("POST", "/users/" + userId + "/ban", "ADMIN", otherId);
        when(req.getInputStream()).thenReturn(
                ru.itis.util.UtilTest.servletStream("{\"reason\":\"RIDE_DANGEROUS\"}"));

        servlet.service(req, resp);

        verify(moderationService).ban(userId, "RIDE_DANGEROUS");
        verify(resp).setStatus(200);
        assertTrue(Util.MAPPER.readTree(out.toString()).get("blocked").asBoolean());
    }

    @Test
    @DisplayName("POST ban без тела -> 400, сервис не вызывается")
    void ban_noBody_400() throws Exception {
        HttpServletRequest req = request("POST", "/users/" + userId + "/ban", "ADMIN", otherId);
        when(req.getInputStream()).thenReturn(ru.itis.util.UtilTest.servletStream(""));

        servlet.service(req, resp);

        verify(resp).setStatus(400);
        verify(moderationService, never()).ban(any(), anyString());
    }

    @Test
    @DisplayName("POST ban с пустой причиной -> 400")
    void ban_blankReason_400() throws Exception {
        HttpServletRequest req = request("POST", "/users/" + userId + "/ban", "ADMIN", otherId);
        when(req.getInputStream()).thenReturn(ru.itis.util.UtilTest.servletStream("{\"reason\":\"  \"}"));

        servlet.service(req, resp);

        verify(resp).setStatus(400);
        verify(moderationService, never()).ban(any(), anyString());
    }

    @Test
    @DisplayName("POST ban: неизвестная причина (ValidationException из сервиса) -> 400")
    void ban_unknownReason_400() throws Exception {
        when(moderationService.ban(userId, "WAT"))
                .thenThrow(new ValidationException("Неизвестная причина блокировки"));
        HttpServletRequest req = request("POST", "/users/" + userId + "/ban", "ADMIN", otherId);
        when(req.getInputStream()).thenReturn(ru.itis.util.UtilTest.servletStream("{\"reason\":\"WAT\"}"));

        servlet.service(req, resp);

        verify(resp).setStatus(400);
    }

    @Test
    @DisplayName("POST unban -> 200, сервис вызван")
    void unban_200() throws Exception {
        when(moderationService.unban(userId)).thenReturn(BlockStatus.notBlocked(userId));

        servlet.service(request("POST", "/users/" + userId + "/unban", "ADMIN", otherId), resp);

        verify(moderationService).unban(userId);
        verify(resp).setStatus(200);
        assertFalse(Util.MAPPER.readTree(out.toString()).get("blocked").asBoolean());
    }

    @Test
    @DisplayName("Неожиданное исключение -> 500 без утечки деталей")
    void unexpectedException_500() throws Exception {
        when(moderationService.getReasons()).thenThrow(new RuntimeException("db is down"));

        servlet.service(request("GET", "/reasons", "ADMIN", userId), resp);

        verify(resp).setStatus(500);
        assertFalse(out.toString().contains("db is down"));
    }

    private HttpServletRequest request(String method, String pathInfo, String role, UUID tokenUserId) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getPathInfo()).thenReturn(pathInfo);
        when(req.getAttribute(AuthFilter.ATTR_ROLE)).thenReturn(role);
        when(req.getAttribute(AuthFilter.ATTR_USER_ID))
                .thenReturn(tokenUserId == null ? null : tokenUserId.toString());
        return req;
    }
}
