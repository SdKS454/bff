package ru.kicksharing.documents.util;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.kicksharing.documents.exception.ApiException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JwtUtilTest {
    public static final UUID USER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final KeyPair KEYS;

    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEYS = generator.generateKeyPair();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        System.setProperty("documents.jwt.public.key", Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
    }

    public static String authorization() {
        return "Bearer " + token("valid");
    }

    private static String token(String variant) {
        var builder = Jwts.builder().subject(USER.toString()).claim("userId", USER.toString())
                .issuer("scooter-user-service").expiration(new Date(System.currentTimeMillis() + 3600000));
        if (variant.equals("issuer")) builder.issuer("other-service");
        if (variant.equals("expiry")) builder.expiration(new Date(1000));
        if (variant.equals("no-expiry")) builder.expiration(null);
        if (variant.equals("subject")) builder.subject(UUID.randomUUID().toString());
        if (variant.equals("short-id")) builder.claim("userId", "1-1-1-1-1");
        if (variant.equals("algorithm")) return builder.signWith(KEYS.getPrivate(), Jwts.SIG.PS256).compact();
        return builder.signWith(KEYS.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @Test
    void acceptsActualUserClaims() {
        assertEquals(USER, JwtUtil.extractUserId(authorization()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "expiry", "no-expiry", "subject", "short-id", "algorithm"})
    void rejectsIncompatibleClaims(String variant) {
        assertEquals(401, assertThrows(ApiException.class, () -> JwtUtil.extractUserId("Bearer " + token(variant))).getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Basic abc", "Bearer ", "Bearer a.b.c"})
    void rejectsMalformedAuthorization(String auth) {
        assertEquals(401, assertThrows(ApiException.class, () -> JwtUtil.extractUserId(auth)).getStatusCode());
    }
}
