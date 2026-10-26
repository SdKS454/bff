package ru.kicksharing.documents.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.kicksharing.documents.exception.ApiException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ClientsTest {
    static final String USER = "550e8400-e29b-41d4-a716-446655440000", RIDE = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    HttpServer server;
    int status; String body, type, bearer, path;
    @BeforeEach void start() throws Exception {
        status = 200; body = "{}"; type = "application/json";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bearer = exchange.getRequestHeaders().getFirst("Authorization"); path = exchange.getRequestURI().toString();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("Location", "/not-followed");
            exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        for (String name : new String[]{"user", "moderation", "ride"})
            System.setProperty("documents." + name + ".service.url", "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }
    void error(int expected, Runnable action) { assertEquals(expected, assertThrows(ApiException.class, action::run).getStatusCode()); }
    @Test void usesValidateAndForwardsOriginalBearer() {
        status = 204; new UserClient().validate("original.jwt.token");
        assertEquals("Bearer original.jwt.token", bearer); assertEquals("/api/users/validate", path);
    }
    @ParameterizedTest @ValueSource(ints={200, 302, 401, 403, 429, 500, 503})
    void invalidValidateResponseNeverAuthorizes(int code) {
        status = code; error(code == 401 ? 401 : 503, () -> new UserClient().validate("token"));
    }
    @ParameterizedTest @ValueSource(ints={302,400,401,403,404,409,429,500,503})
    void downstreamStatusMapping(int code) {
        status = code;
        int expected = code == 429 || code >= 500 ? 503 : code == 302 || code == 400 ? 502 : code;
        error(expected, () -> new UserClient().getUser(USER, "token"));
    }
    @ParameterizedTest @ValueSource(strings={"null","[]","{broken","{} {}","{\"id\":\"x\",\"id\":\"y\"}","{}"})
    void malformedSuccessfulUserResponseIs502(String json) {
        body = json; error(502, () -> new UserClient().getUser(USER, "token"));
    }
    @Test void wrongContentTypeIs502() {
        type = "text/html"; error(502, () -> new UserClient().getUser(USER,"token"));
    }
    @Test void actualUserShapeAllowsAdditionalFields() {
        body = "{\"id\":\""+USER+"\",\"login\":\"ivan\",\"fullName\":\"Иван\",\"blocked\":true,\"role\":\"USER\"}";
        assertEquals("Иван", new UserClient().getUser(USER,"token").getFullName());
    }
    @ParameterizedTest @ValueSource(strings={"{}", "{\"blocked\":\"false\"}", "{\"blocked\":false}", "{\"userId\":\"wrong\",\"blocked\":false}"})
    void malformedModerationCannotBecomeNotBlocked(String json) {
        body = json; error(502, () -> new ModerationClient().getStatus(USER,"token"));
    }
    @Test void actualUnblockedModerationShapeWorks() {
        body = "{\"userId\":\""+USER+"\",\"blocked\":false,\"blockedUntil\":null}";
        assertFalse(new ModerationClient().getStatus(USER,"token").isBlocked());
    }
    @Test void paidRideNeedsPaymentFields() {
        body = "{\"id\":\""+RIDE+"\",\"userId\":\""+USER+"\",\"status\":\"PAID\",\"numberPlate\":\"A\",\"startedAt\":\"2026-10-20T12:00:00Z\"}";
        error(502, () -> new RideClient().getRide(RIDE,"token"));
    }
    @Test void foreignHistoryCannotBecomeAnEmptySuccess() {
        body = "[{\"id\":\""+RIDE+"\",\"userId\":\""+RIDE+"\",\"status\":\"ACTIVE\",\"numberPlate\":\"A\",\"startedAt\":\"2026-10-20T12:00:00Z\"}]";
        error(502, () -> new RideClient().getUserRides(USER,"token"));
    }
}
