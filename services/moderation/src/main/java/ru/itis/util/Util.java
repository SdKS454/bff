package ru.itis.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.itis.dto.ErrorResponse;
import ru.itis.exception.ApiException;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;

public class Util {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public static void sendJson(HttpServletResponse resp, int status, Object body) throws IOException {
        sendRaw(resp, status, MAPPER.writeValueAsString(body));
    }

    public static void sendRaw(HttpServletResponse resp, int status, String json) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(json);
    }

    public static void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        sendJson(resp, status, new ErrorResponse(message));
    }

    public static UUID parseUuid(String text) {
        if (text == null || !UUID_PATTERN.matcher(text).matches()) {
            throw new ApiException(400, "Некорректный userId");
        }
        return UUID.fromString(text);
    }

    public static int parseInt(String text, int defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Некорректные limit или offset");
        }
    }

    public static <T> T readBody(HttpServletRequest req, Class<T> type) throws IOException {
        byte[] bytes = req.getInputStream().readAllBytes();
        if (bytes.length == 0) {
            return null;
        }
        try {
            return MAPPER.readValue(bytes, type);
        } catch (JsonProcessingException e) {
            throw new ApiException(400, "Некорректное тело запроса");
        }
    }

    public static String formatDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
