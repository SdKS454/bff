package ru.itis.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    static String token(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    @Test
    @DisplayName("readPayload: корректный токен -> JSON payload")
    void readPayload_valid() {
        JsonNode payload = JwtUtil.readPayload(token("{\"userId\":\"abc\",\"role\":\"ADMIN\"}"));

        assertNotNull(payload);
        assertEquals("abc", payload.get("userId").asText());
        assertEquals("ADMIN", payload.get("role").asText());
    }

    @Test
    @DisplayName("readPayload: токен из двух частей (без подписи) тоже читается")
    void readPayload_twoParts() {
        String full = token("{\"userId\":\"abc\"}");
        String twoParts = full.substring(0, full.lastIndexOf('.'));

        assertNotNull(JwtUtil.readPayload(twoParts));
    }

    @Test
    @DisplayName("readPayload: токен без точек -> null")
    void readPayload_noDots() {
        assertNull(JwtUtil.readPayload("justonestring"));
    }

    @Test
    @DisplayName("readPayload: payload не Base64 -> null")
    void readPayload_invalidBase64() {
        assertNull(JwtUtil.readPayload("header.@@@###.sig"));
    }

    @Test
    @DisplayName("readPayload: payload не JSON -> null")
    void readPayload_notJson() {
        String bad = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("this is not json".getBytes(StandardCharsets.UTF_8));

        assertNull(JwtUtil.readPayload("header." + bad + ".sig"));
    }

    @Test
    @DisplayName("readPayload: null вместо токена -> null (исключение не пробрасывается)")
    void readPayload_null() {
        assertNull(JwtUtil.readPayload(null));
    }
}
