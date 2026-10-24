package ru.kicksharing.documents.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import ru.kicksharing.documents.config.AppConfig;
import ru.kicksharing.documents.exception.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

public final class JwtUtil {
    private static volatile PublicKey cachedKey;
    private JwtUtil() {}

    private static PublicKey getKey() {
        if (cachedKey != null) return cachedKey;
        synchronized (JwtUtil.class) {
            if (cachedKey != null) return cachedKey;
            try {
                String pem = AppConfig.get("jwt.public.key");
                String path = AppConfig.get("jwt.public.key.path");
                if (path != null && !path.isBlank()) pem = Files.readString(Path.of(path));
                if (pem == null || pem.isBlank()) throw new IllegalStateException("Set JWT_PUBLIC_KEY or JWT_PUBLIC_KEY_PATH");
                String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s+", "");
                cachedKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
                return cachedKey;
            } catch (Exception e) { throw new IllegalStateException("Не настроен публичный ключ RS256", e); }
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
        } catch (IllegalStateException e) { throw ApiException.serviceUnavailable("Не настроен публичный ключ JWT"); }
        catch (Exception e) { throw new ApiException(401, "Недействительный JWT"); }
    }
}
