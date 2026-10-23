package ru.itis.security;

import com.fasterxml.jackson.databind.JsonNode;
import ru.itis.util.Util;

import java.util.Base64;

public class JwtUtil {

    public static JsonNode readPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] json = Base64.getUrlDecoder().decode(parts[1]);
            return Util.MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
