package ru.kicksharing.documents.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import ru.kicksharing.documents.config.AppConfig;
import ru.kicksharing.documents.exception.ApiException;

import java.security.PublicKey;
import java.util.UUID;

public final class JwtUtil {
    private static volatile PublicKey cachedKey;

    private JwtUtil() {
    }

    private static PublicKey getKey() {
        if (cachedKey != null) return cachedKey;
        synchronized (JwtUtil.class) {
            if (cachedKey != null) return cachedKey;
            cachedKey = JwtPublicKeyLoader.load(AppConfig.get("jwt.public.key"), AppConfig.get("jwt.public.key.path"));
            return cachedKey;
        }
    }

    public static UUID extractUserId(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7))
            throw new ApiException(401, "Ожидается Authorization: Bearer <JWT>");
        String token = authorization.substring(7).trim();
        if (token.isBlank()) throw new ApiException(401, "Пустой JWT");
        try {
            var signed = Jwts.parser().verifyWith(getKey())
                    .requireIssuer(AppConfig.get("jwt.issuer")).build().parseSignedClaims(token);
            if (!"RS256".equals(signed.getHeader().getAlgorithm())) throw new IllegalArgumentException();
            Claims claims = signed.getPayload();
            if (claims.getExpiration() == null) throw new IllegalArgumentException();
            String value = claims.get("userId", String.class);
            if (value == null || !value.matches("[0-9a-fA-F-]{36}") || !value.equals(claims.getSubject()))
                throw new IllegalArgumentException();
            return UUID.fromString(value);
        } catch (IllegalStateException e) {
            java.util.logging.Logger.getLogger(JwtUtil.class.getName()).log(java.util.logging.Level.WARNING, "Documents JWT configuration failure", e);
            throw ApiException.serviceUnavailable(e.getMessage());
        } catch (Exception e) {
            throw new ApiException(401, "Недействительный JWT");
        }
    }
}
