package ru.scooter.user.util;

import io.jsonwebtoken.*;

import ru.scooter.user.entity.Role;
import ru.scooter.user.exception.ApiException;

import java.security.*;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class JwtUtil {
    public record IssuedToken(String value, UUID id, Instant issuedAt, Instant expiresAt) {}

    public record VerifiedToken(UUID userId, Role role, UUID tokenId, Instant expiresAt) {}

    private final PrivateKey privateKey;
    private final JwtParser parser;
    private final String issuer;
    private final long ttlSeconds;
    private final Clock clock;

    public JwtUtil(
            PrivateKey privateKey,
            PublicKey publicKey,
            String issuer,
            long ttlSeconds,
            Clock clock) {
        this.privateKey = privateKey;
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
        this.parser =
                Jwts.parser()
                        .verifyWith(publicKey)
                        .requireIssuer(issuer)
                        .clock(() -> Date.from(clock.instant()))
                        .sig()
                        .clear()
                        .add(Jwts.SIG.RS256)
                        .and()
                        .build();
        // Fail at startup rather than issuing tokens nobody can verify when the key pair differs.
        verify(issue(UUID.randomUUID(), Role.USER).value());
    }

    public IssuedToken issue(UUID userId, Role role) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expires = now.plusSeconds(ttlSeconds);
        UUID id = UUID.randomUUID();
        String jwt =
                Jwts.builder()
                        .header()
                        .type("JWT")
                        .and()
                        .issuer(issuer)
                        .subject(userId.toString())
                        .id(id.toString())
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expires))
                        .claim("userId", userId.toString())
                        .claim("role", role.name())
                        .signWith(privateKey, Jwts.SIG.RS256)
                        .compact();
        return new IssuedToken(jwt, id, now, expires);
    }

    public VerifiedToken verify(String token) {
        try {
            if (token == null || token.length() > 8192) throw new IllegalArgumentException();
            Jws<Claims> signed = parser.parseSignedClaims(token);
            Claims claims = signed.getPayload();
            UUID userId = UUID.fromString(claims.get("userId", String.class));
            Role role = Role.valueOf(claims.get("role", String.class));
            UUID id = UUID.fromString(claims.getId());
            if (!"RS256".equals(signed.getHeader().getAlgorithm())
                    || !userId.toString().equals(claims.getSubject())
                    || claims.getExpiration() == null
                    || claims.getIssuedAt() == null
                    || !claims.getExpiration().toInstant().isAfter(clock.instant())
                    || claims.getIssuedAt().toInstant().isAfter(clock.instant().plusSeconds(30)))
                throw new IllegalArgumentException();
            return new VerifiedToken(userId, role, id, claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new ApiException(401, "Недействительный или просроченный токен");
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
