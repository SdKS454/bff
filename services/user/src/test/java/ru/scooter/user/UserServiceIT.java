package ru.scooter.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ru.scooter.user.cli.GenerateKeys;
import ru.scooter.user.util.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceIT {
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicInteger moderationMode = new AtomicInteger(200);
    private final AtomicReference<String> forwardedAuth = new AtomicReference<>();
    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    private String base, schema, userId, adminId, userToken, adminToken;
    private HttpServer moderation;
    private Process server;
    private Path secrets;
    private ExecutorService readers;
    private String dbUrl, dbUser, dbPassword;

    @BeforeAll
    void start() throws Exception {
        dbUrl = System.getenv("TEST_DB_URL");
        dbUser = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
        dbPassword = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");
        Class.forName("org.postgresql.Driver");
        schema = "user_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = connection();
             Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
            s.execute("SET search_path TO " + schema);
            s.execute(Files.readString(Path.of("db/001_schema.sql")));
        }
        moderation = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        moderation.createContext(
                "/api/moderation",
                exchange -> {
                    int code = moderationMode.get();
                    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                    forwardedAuth.set(authorization);
                    String path = exchange.getRequestURI().getPath();
                    String body;
                    if (code != 200) body = "{\"error\":\"Модерация временно недоступна\"}";
                    else if (path.endsWith("/reasons"))
                        body =
                                "[{\"reasonType\":\"RIDE_DANGEROUS\",\"description\":\"Опасное"
                                        + " вождение\",\"durationSeconds\":604800}]";
                    else if (path.equals("/api/moderation/users")) body = "[]";
                    else {
                        String[] parts = path.split("/");
                        String id = parts[4];
                        if (path.endsWith("/ban")) {
                            JsonNode input = Json.MAPPER.readTree(exchange.getRequestBody());
                            if (!"RIDE_DANGEROUS".equals(input.path("reason").asText())) {
                                code = 400;
                            } else blocked.add(id);
                        }
                        if (path.endsWith("/unban")) blocked.remove(id);
                        body =
                                code == 400
                                        ? "{\"error\":\"Неизвестная причина\"}"
                                        : Json.MAPPER.writeValueAsString(
                                        Map.of(
                                                "userId",
                                                id,
                                                "blocked",
                                                blocked.contains(id)));
                    }
                    byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(code, bytes.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        moderation.start();
        secrets = Files.createTempDirectory("user-it-keys-");
        GenerateKeys.main(new String[]{secrets.toString()});
        ProcessBuilder process =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "--add-opens=java.base/java.io=ALL-UNNAMED",
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                        "--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED",
                        "-cp",
                        System.getProperty("java.class.path"),
                        WarTestServer.class.getName());
        Map<String, String> env = process.environment();
        env.put("DB_URL", dbUrl + (dbUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        env.put("DB_USER", dbUser);
        env.put("DB_PASSWORD", dbPassword.isBlank() ? "test-password" : dbPassword);
        env.put("JWT_PRIVATE_KEY_PATH", secrets.resolve("private.pem").toString());
        env.put("JWT_PUBLIC_KEY_PATH", secrets.resolve("public.pem").toString());
        env.put("JWT_ISSUER", "scooter-user-service");
        env.put("JWT_TTL_SECONDS", "3600");
        env.put("MODERATION_BASE_URL", "http://127.0.0.1:" + moderation.getAddress().getPort());
        env.put("CORS_ALLOWED_ORIGINS", "http://localhost:8082");
        env.put("BCRYPT_COST", "10");
        env.put("MODERATION_TIMEOUT_SECONDS", "1");
        process.redirectError(Path.of("target/it-server.log").toFile());
        server = process.start();
        readers = Executors.newSingleThreadExecutor();
        Future<String> port =
                readers.submit(
                        () -> {
                            try (BufferedReader reader = server.inputReader()) {
                                String line;
                                while ((line = reader.readLine()) != null)
                                    if (line.startsWith("USER_TEST_PORT="))
                                        return line.substring(15);
                            }
                            throw new IllegalStateException(
                                    "WAR did not start; see target/it-server.log");
                        });
        base = "http://127.0.0.1:" + port.get(45, TimeUnit.SECONDS);
        userId = json(register("normal_user", "ADMIN")).path("id").asText();
        adminId = json(register("real_admin", "USER")).path("id").asText();
        update("UPDATE users SET role='ADMIN' WHERE login='real_admin'");
        userToken = json(login("normal_user")).path("accessToken").asText();
        adminToken = json(login("real_admin")).path("accessToken").asText();
        assertFalse(userToken.isBlank());
        assertFalse(adminToken.isBlank());
    }

    @AfterEach
    void resetModeration() {
        moderationMode.set(200);
        blocked.clear();
    }

    @AfterAll
    void stop() throws Exception {
        if (server != null) {
            server.destroy();
            if (!server.waitFor(10, TimeUnit.SECONDS)) server.destroyForcibly();
        }
        if (readers != null) readers.shutdownNow();
        if (moderation != null) moderation.stop(0);
        if (schema != null)
            try (Connection c = connection();
                 Statement s = c.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        if (secrets != null)
            try (var paths = Files.walk(secrets)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
    }

    @Test
    void registrationCannotEscalatePrivilegesAndHandlesDuplicateLogin() throws Exception {
        HttpResponse<String> response = request("GET", "/api/users/me", null, userToken, null);
        assertEquals(200, response.statusCode());
        assertEquals("USER", json(response).path("role").asText());
        assertEquals(userId, json(response).path("id").asText());
        assertEquals(409, register("NORMAL_USER", "USER").statusCode());
        assertEquals(
                401,
                request(
                        "POST",
                        "/api/users/login",
                        Map.of("login", "normal_user", "password", "wrong-pass"),
                        null,
                        null)
                        .statusCode());
        assertTrue(
                json(request("GET", "/api/users/exists?login=NORMAL_USER", null, null, null))
                        .path("exists")
                        .booleanValue());
    }

    @Test
    void listIsAdminOnlyAndContainsOnlyPublicSummary() throws Exception {
        assertEquals(401, request("GET", "/api/users", null, null, null).statusCode());
        assertEquals(403, request("GET", "/api/users", null, userToken, null).statusCode());
        JsonNode users = json(request("GET", "/api/users", null, adminToken, null));
        assertTrue(users.isArray());
        assertEquals(2, users.size());
        Set<String> fields = new HashSet<>();
        users.get(0).fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id", "login", "fullName"), fields);
        assertEquals(
                403, request("GET", "/api/users/" + adminId, null, userToken, null).statusCode());
        assertEquals(
                404,
                request("GET", "/api/users/" + UUID.randomUUID(), null, adminToken, null)
                        .statusCode());
    }

    @Test
    void validationDoesNotCallModerationButProfileReturns503OnFailure() throws Exception {
        moderationMode.set(503);
        forwardedAuth.set(null);
        assertEquals(
                204, request("GET", "/api/users/validate", null, userToken, null).statusCode());
        assertNull(forwardedAuth.get());
        assertEquals(
                503,
                request("GET", "/api/users/" + userId + "/blocked", null, userToken, null)
                        .statusCode());
        assertEquals(503, request("GET", "/api/users/me", null, userToken, null).statusCode());
        assertEquals(503, login("normal_user").statusCode());
    }

    @Test
    void moderationTokenIsForwardedAndBansAreProxied() throws Exception {
        assertEquals(
                403,
                request(
                        "POST",
                        "/api/users/" + userId + "/ban",
                        Map.of("reason", "RIDE_DANGEROUS"),
                        userToken,
                        null)
                        .statusCode());
        assertEquals(
                200,
                request(
                        "POST",
                        "/api/users/" + userId + "/ban",
                        Map.of("reason", "RIDE_DANGEROUS"),
                        adminToken,
                        null)
                        .statusCode());
        assertEquals("Bearer " + adminToken, forwardedAuth.get());
        assertTrue(
                json(request("GET", "/api/users/" + userId + "/blocked", null, userToken, null))
                        .path("blocked")
                        .booleanValue());
        assertEquals("Bearer " + userToken, forwardedAuth.get());
        assertEquals(
                200,
                request("GET", "/api/users/blocked?limit=10&offset=0", null, adminToken, null)
                        .statusCode());
        assertEquals(
                200, request("GET", "/api/users/reasons", null, adminToken, null).statusCode());
        assertEquals(
                200,
                request("POST", "/api/users/" + userId + "/unban", null, adminToken, null)
                        .statusCode());
        assertFalse(
                json(request("GET", "/api/users/me", null, userToken, null))
                        .path("blocked")
                        .booleanValue());
    }

    @Test
    void corsPreflightDoesNotNeedJwtAndHeadersSurvive401() throws Exception {
        HttpRequest preflight =
                HttpRequest.newBuilder(URI.create(base + "/api/users"))
                        .header("Origin", "http://localhost:8082")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization, content-type")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build();
        HttpResponse<String> response = http.send(preflight, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());
        assertEquals(
                "http://localhost:8082",
                response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(
                response.headers()
                        .firstValue("Access-Control-Allow-Headers")
                        .orElseThrow()
                        .contains("Authorization"));
        HttpResponse<String> denied =
                request("GET", "/api/users", null, null, "http://localhost:8082");
        assertEquals(401, denied.statusCode());
        assertEquals(
                "http://localhost:8082",
                denied.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        HttpResponse<String> evil =
                request("GET", "/api/users", null, adminToken, "https://evil.example");
        assertEquals(403, evil.statusCode());
        assertTrue(evil.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void standardAdminLoginWorksWithoutAnyUserFrontend() throws Exception {
        JsonNode result = json(login("real_admin"));
        assertEquals("ADMIN", result.path("user").path("role").asText());
        assertEquals(
                204,
                request(
                        "GET",
                        "/api/users/validate",
                        null,
                        result.path("accessToken").asText(),
                        null)
                        .statusCode());
        assertEquals(404, request("GET", "/login.html", null, null, null).statusCode());
        assertEquals(404, request("GET", "/login.js", null, null, null).statusCode());
        assertEquals(404, request("GET", "/login.css", null, null, null).statusCode());
    }

    @Test
    void logoutRevokesStoredTokenAndTamperingFails() throws Exception {
        String token = json(login("normal_user")).path("accessToken").asText();
        assertEquals(204, request("POST", "/api/users/logout", null, token, null).statusCode());
        assertEquals(401, request("GET", "/api/users/validate", null, token, null).statusCode());
        String[] parts = userToken.split("\\.");
        String payload =
                new String(
                        Base64.getUrlDecoder().decode(parts[1]),
                        java.nio.charset.StandardCharsets.UTF_8)
                        .replace("USER", "ADMIN");
        String forged =
                parts[0]
                        + "."
                        + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "."
                        + parts[2];
        assertEquals(401, request("GET", "/api/users", null, forged, null).statusCode());
    }

    @Test
    void softDeletionAndRoleChangeInvalidateTokens() throws Exception {
        register("role_test", "USER");
        String token = json(login("role_test")).path("accessToken").asText();
        update("UPDATE users SET role='ADMIN' WHERE login='role_test'");
        assertEquals(401, request("GET", "/api/users/validate", null, token, null).statusCode());
        token = json(login("role_test")).path("accessToken").asText();
        update(
                "UPDATE users SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP WHERE"
                        + " login='role_test'");
        assertEquals(401, request("GET", "/api/users/validate", null, token, null).statusCode());
        assertEquals(401, login("role_test").statusCode());

    }

    @Test
    void invalidJsonAndUnsupportedMethodsAreJsonErrors() throws Exception {
        HttpRequest bad =
                HttpRequest.newBuilder(URI.create(base + "/api/users/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{oops"))
                        .build();
        HttpResponse<String> response = http.send(bad, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(json(response).has("error"));
        assertEquals(405, request("PUT", "/api/users/me", null, userToken, null).statusCode());
        assertEquals(
                400, request("GET", "/api/users/1-1-1-1-1", null, adminToken, null).statusCode());
    }

    @Test
    void concurrentRegistrationsHaveOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first =
                    pool.submit(() -> register("concurrent_user", "USER").statusCode());
            Future<Integer> second =
                    pool.submit(() -> register("concurrent_user", "USER").statusCode());
            List<Integer> statuses = new ArrayList<>(List.of(first.get(), second.get()));
            Collections.sort(statuses);
            assertEquals(List.of(201, 409), statuses);
        } finally {
            pool.shutdownNow();
            update(
                    "UPDATE users SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP WHERE"
                            + " login='concurrent_user'");
        }
    }

    @Test
    void databaseFailureReturnsJson503() throws Exception {
        update("ALTER TABLE tokens RENAME TO temporarily_unavailable_tokens");
        try {
            HttpResponse<String> response =
                    request("GET", "/api/users/validate", null, userToken, null);
            assertEquals(503, response.statusCode());
            assertTrue(json(response).has("error"));
        } finally {
            update("ALTER TABLE temporarily_unavailable_tokens RENAME TO tokens");
        }
    }

    @Test
    void databaseHasOnlyUsersAndTokensAndTokenCanBeRevokedByFlag() throws Exception {
        String value = json(login("normal_user")).path("accessToken").asText();
        String hash = ru.scooter.user.util.TokenHash.sha256(value);
        try (Connection c = connection();
             Statement statement = c.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            try (ResultSet rs =
                         statement.executeQuery(
                                 "SELECT table_name FROM information_schema.tables WHERE table_schema='"
                                         + schema
                                         + "' AND table_type='BASE TABLE'")) {
                Set<String> tables = new HashSet<>();
                while (rs.next()) tables.add(rs.getString(1));
                assertEquals(Set.of("users", "tokens"), tables);
            }
            try (PreparedStatement select =
                         c.prepareStatement(
                                 "SELECT user_id,token_hash,expire_at,is_valid FROM tokens WHERE"
                                         + " token_hash=?")) {
                select.setString(1, hash);
                try (ResultSet rs = select.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(userId, rs.getObject("user_id").toString());
                    assertEquals(hash, rs.getString("token_hash"));
                    assertTrue(rs.getBoolean("is_valid"));
                    assertTrue(
                            rs.getTimestamp("expire_at")
                                    .toInstant()
                                    .isAfter(java.time.Instant.now()));
                }
            }
            try (PreparedStatement revoke =
                         c.prepareStatement("UPDATE tokens SET is_valid=FALSE WHERE token_hash=?")) {
                revoke.setString(1, hash);
                assertEquals(1, revoke.executeUpdate());
            }
        }
        assertEquals(401, request("GET", "/api/users/validate", null, value, null).statusCode());
    }

    @Test
    void oldExpiryColumnMigrationPreservesTokens() throws Exception {
        try (Connection c = connection();
             Statement statement = c.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("ALTER TABLE tokens RENAME COLUMN expire_at TO expires_at");
            statement.execute("ALTER INDEX tokens_expire_at_idx RENAME TO tokens_expires_at_idx");
            statement.execute(Files.readString(Path.of("db/002_rename_token_expiry.sql")));
        }
        assertEquals(
                204, request("GET", "/api/users/validate", null, userToken, null).statusCode());
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private void update(String sql) throws SQLException {
        try (Connection c = connection();
             Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + schema);
            s.executeUpdate(sql);
        }
    }

    private HttpResponse<String> register(String login, String role) throws Exception {
        return request(
                "POST",
                "/api/users/register",
                Map.of(
                        "login",
                        login,
                        "password",
                        "password123",
                        "fullName",
                        "Иван Петров",
                        "birthDate",
                        "2000-05-17",
                        "role",
                        role),
                null,
                null);
    }

    private HttpResponse<String> login(String login) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("login", login);
        body.put("password", "password123");
        body.put("fullName", null);
        body.put("birthDate", null);
        body.put("avatarUrl", null);
        body.put("role", null);
        return request("POST", "/api/users/login", body, null, null);
    }

    private JsonNode json(HttpResponse<String> response) throws IOException {
        return Json.MAPPER.readTree(response.body());
    }

    private HttpResponse<String> request(
            String method, String path, Object body, String token, String origin) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (origin != null) request.header("Origin", origin);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else
            request.header("Content-Type", "application/json")
                    .method(
                            method,
                            HttpRequest.BodyPublishers.ofString(
                                    Json.MAPPER.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
