package ru.scooter.user.util;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.*;

import ru.scooter.user.exception.ApiException;

import java.io.IOException;
import java.util.Map;

public final class Json {
    public static final ObjectMapper MAPPER =
            JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build();

    private Json() {}

    public static <T> T read(HttpServletRequest req, Class<T> type) throws IOException {
        String contentType = req.getContentType();
        if (contentType == null
                || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
            throw new ApiException(415, "Ожидается Content-Type: application/json");
        byte[] bytes = req.getInputStream().readNBytes(16_385);
        if (bytes.length > 16_384) throw new ApiException(413, "Тело запроса слишком большое");
        try {
            T result = MAPPER.readValue(bytes, type);
            if (result == null) throw new ApiException(400, "Ожидается JSON-объект");
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ApiException(400, "Некорректный JSON или типы полей");
        }
    }

    public static void write(HttpServletResponse resp, int status, Object value)
            throws IOException {
        resp.setStatus(status);
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setHeader("Cache-Control", "no-store");
        MAPPER.writeValue(resp.getOutputStream(), value);
    }

    public static void error(HttpServletResponse resp, int status, String message)
            throws IOException {
        if (status == 401) resp.setHeader("WWW-Authenticate", "Bearer");
        write(resp, status, Map.of("error", message));
    }
}
