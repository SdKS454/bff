package ru.scooter.user.config;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record AppConfig(
        String dbUrl,
        String dbUser,
        String dbPassword,
        Path privateKeyPath,
        Path publicKeyPath,
        String jwtIssuer,
        long jwtTtlSeconds,
        Set<String> allowedOrigins,
        URI moderationBaseUrl,
        int moderationTimeoutSeconds,
        int bcryptCost) {
    public static AppConfig fromEnvironment() {
        Map<String, String> env = System.getenv();
        URI base = httpUri(value(env, "MODERATION_BASE_URL", "http://localhost:8082"));
        if (base.getQuery() != null || base.getFragment() != null || base.toString().endsWith("/"))
            throw new IllegalArgumentException(
                    "MODERATION_BASE_URL: use base URL without query, fragment or trailing slash");
        Set<String> origins =
                Arrays.stream(
                                value(env, "CORS_ALLOWED_ORIGINS", "http://localhost:8082")
                                        .split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        for (String origin : origins) {
            URI uri = httpUri(origin);
            if (!(uri.getPath().isEmpty()) || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException(
                        "CORS origins must contain only scheme, host and port");
        }
        String dbUrl = required(env, "DB_URL");
        if (!dbUrl.startsWith("jdbc:postgresql:"))
            throw new IllegalArgumentException("DB_URL must use PostgreSQL JDBC");
        return new AppConfig(
                dbUrl,
                required(env, "DB_USER"),
                required(env, "DB_PASSWORD"),
                Path.of(required(env, "JWT_PRIVATE_KEY_PATH")),
                Path.of(required(env, "JWT_PUBLIC_KEY_PATH")),
                value(env, "JWT_ISSUER", "scooter-user-service"),
                number(env, "JWT_TTL_SECONDS", 3600, 60, 86400),
                origins,
                base,
                (int) number(env, "MODERATION_TIMEOUT_SECONDS", 3, 1, 5),
                (int) number(env, "BCRYPT_COST", 12, 10, 14));
    }

    private static URI httpUri(String text) {
        URI uri = URI.create(text);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Expected absolute HTTP(S) URL without credentials");
        return uri;
    }

    private static String required(Map<String, String> env, String key) {
        String result = env.get(key);
        if (result == null || result.isBlank())
            throw new IllegalArgumentException("Missing environment variable: " + key);
        return result;
    }

    private static String value(Map<String, String> env, String key, String fallback) {
        String result = env.get(key);
        return result == null || result.isBlank() ? fallback : result;
    }

    private static long number(
            Map<String, String> env, String key, long fallback, long min, long max) {
        long result = Long.parseLong(value(env, key, Long.toString(fallback)));
        if (result < min || result > max)
            throw new IllegalArgumentException("Invalid environment variable: " + key);
        return result;
    }
}
