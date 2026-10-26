package ru.scooter.user;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;

import org.junit.jupiter.api.*;

import ru.scooter.user.entity.Role;
import ru.scooter.user.exception.ApiException;
import ru.scooter.user.util.JwtUtil;

import java.security.*;
import java.time.*;
import java.util.*;

class JwtUtilTest {
    private static KeyPair keys;
    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    private JwtUtil jwt(Clock clock) {
        return new JwtUtil(keys.getPrivate(), keys.getPublic(), "test-user-service", 3600, clock);
    }

    @Test
    void tokenHasExactIntegrationClaims() {
        UUID id = UUID.randomUUID();
        JwtUtil util = jwt(CLOCK);
        JwtUtil.IssuedToken token = util.issue(id, Role.ADMIN);
        var claims =
                Jwts.parser()
                        .verifyWith(keys.getPublic())
                        .clock(() -> Date.from(NOW))
                        .build()
                        .parseSignedClaims(token.value())
                        .getPayload();
        assertEquals(id.toString(), claims.get("userId"));
        assertEquals("ADMIN", claims.get("role"));
        assertEquals(id.toString(), claims.getSubject());
        assertNotNull(claims.getId());
        assertEquals(id, util.verify(token.value()).userId());
        assertEquals(NOW.plusSeconds(3600), token.expiresAt());
    }

    @Test
    void rejectsExpiredToken() {
        String token = jwt(CLOCK).issue(UUID.randomUUID(), Role.USER).value();
        assertEquals(
                401,
                assertThrows(
                                ApiException.class,
                                () -> jwt(Clock.offset(CLOCK, Duration.ofHours(2))).verify(token))
                        .status());
    }

    @Test
    void rejectsWrongSignature() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair other = generator.generateKeyPair();
        String token =
                new JwtUtil(other.getPrivate(), other.getPublic(), "test-user-service", 3600, CLOCK)
                        .issue(UUID.randomUUID(), Role.ADMIN)
                        .value();
        assertThrows(ApiException.class, () -> jwt(CLOCK).verify(token));
    }

    @Test
    void rejectsWrongIssuerMissingRoleAndNonJwt() {
        String wrongIssuer =
                new JwtUtil(keys.getPrivate(), keys.getPublic(), "other", 3600, CLOCK)
                        .issue(UUID.randomUUID(), Role.ADMIN)
                        .value();
        assertThrows(ApiException.class, () -> jwt(CLOCK).verify(wrongIssuer));
        String missing =
                Jwts.builder()
                        .issuer("test-user-service")
                        .expiration(Date.from(NOW.plusSeconds(600)))
                        .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                        .compact();
        assertThrows(ApiException.class, () -> jwt(CLOCK).verify(missing));
        assertThrows(ApiException.class, () -> jwt(CLOCK).verify("not-a-jwt"));
    }
}
