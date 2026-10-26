package ru.itis.util;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.itis.dto.BanRequest;
import ru.itis.exception.ApiException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UtilTest {

    @Test
    @DisplayName("parseUuid: корректный UUID (в т.ч. в верхнем регистре) разбирается")
    void parseUuid_valid() {
        UUID id = UUID.randomUUID();

        assertEquals(id, Util.parseUuid(id.toString()));
        assertEquals(id, Util.parseUuid(id.toString().toUpperCase()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "123", "not-a-uuid",
            "123e4567-e89b-12d3-a456-42661417400",
            "123e4567-e89b-12d3-a456-4266141740000",
            "123e4567-e89b-12d3-a456-42661417400z"})
    @DisplayName("parseUuid: некорректная строка -> ApiException 400")
    void parseUuid_invalid(String text) {
        ApiException ex = assertThrows(ApiException.class, () -> Util.parseUuid(text));
        assertEquals(400, ex.getStatus());
    }

    @Test
    @DisplayName("parseUuid: null -> ApiException 400")
    void parseUuid_null() {
        ApiException ex = assertThrows(ApiException.class, () -> Util.parseUuid(null));
        assertEquals(400, ex.getStatus());
    }


    @Test
    @DisplayName("parseInt: null и пустая строка -> значение по умолчанию")
    void parseInt_defaults() {
        assertEquals(50, Util.parseInt(null, 50));
        assertEquals(50, Util.parseInt("", 50));
        assertEquals(50, Util.parseInt("   ", 50));
    }

    @Test
    @DisplayName("parseInt: число с пробелами и отрицательное разбираются")
    void parseInt_valid() {
        assertEquals(10, Util.parseInt("10", 0));
        assertEquals(10, Util.parseInt("  10  ", 0));
        assertEquals(-3, Util.parseInt("-3", 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "10x", "99999999999"})
    @DisplayName("parseInt: не число / переполнение -> ApiException 400")
    void parseInt_invalid(String text) {
        ApiException ex = assertThrows(ApiException.class, () -> Util.parseInt(text, 0));
        assertEquals(400, ex.getStatus());
    }

    @Test
    @DisplayName("formatDate: null -> null")
    void formatDate_null() {
        assertNull(Util.formatDate(null));
    }

    @Test
    @DisplayName("formatDate: усекает до секунд, формат ISO-8601")
    void formatDate_truncatesToSeconds() {
        Instant instant = Instant.parse("2026-03-15T10:20:30.987654321Z");

        assertEquals("2026-03-15T10:20:30Z", Util.formatDate(instant));
    }


    @Test
    @DisplayName("readBody: валидный JSON десериализуется в DTO")
    void readBody_validJson() throws IOException {
        HttpServletRequest req = requestWithBody("{\"reason\":\"RIDE_DANGEROUS\"}");

        BanRequest body = Util.readBody(req, BanRequest.class);

        assertNotNull(body);
        assertEquals("RIDE_DANGEROUS", body.getReason());
    }

    @Test
    @DisplayName("readBody: пустое тело -> null")
    void readBody_emptyBody() throws IOException {
        assertNull(Util.readBody(requestWithBody(""), BanRequest.class));
    }

    @Test
    @DisplayName("readBody: неизвестные поля игнорируются (FAIL_ON_UNKNOWN_PROPERTIES=false)")
    void readBody_unknownFieldsIgnored() throws IOException {
        HttpServletRequest req = requestWithBody("{\"reason\":\"X\",\"extra\":123}");

        BanRequest body = Util.readBody(req, BanRequest.class);

        assertEquals("X", body.getReason());
    }

    @Test
    @DisplayName("readBody: битый JSON -> ApiException 400")
    void readBody_brokenJson() throws IOException {
        HttpServletRequest req = requestWithBody("{not json");

        ApiException ex = assertThrows(ApiException.class, () -> Util.readBody(req, BanRequest.class));
        assertEquals(400, ex.getStatus());
    }

    @Test
    @DisplayName("sendJson: ставит статус, content-type и пишет JSON")
    void sendJson_writesResponse() throws IOException {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));

        Util.sendJson(resp, 200, new BanRequest("TEST"));

        verify(resp).setStatus(200);
        verify(resp).setContentType("application/json");
        verify(resp).setCharacterEncoding("UTF-8");
        assertEquals("{\"reason\":\"TEST\"}", out.toString());
    }

    @Test
    @DisplayName("sendError: тело ответа содержит сообщение об ошибке")
    void sendError_writesMessage() throws IOException {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));

        Util.sendError(resp, 404, "Не найдено");

        verify(resp).setStatus(404);
        assertTrue(out.toString().contains("Не найдено"));
    }

    static HttpServletRequest requestWithBody(String body) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getInputStream()).thenReturn(servletStream(body));
        return req;
    }

    public static ServletInputStream servletStream(String body) {
        ByteArrayInputStream in = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override public boolean isFinished() { return in.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener readListener) { }
            @Override public int read() { return in.read(); }
        };
    }
}
