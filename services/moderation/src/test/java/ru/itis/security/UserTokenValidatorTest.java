package ru.itis.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.itis.exception.ApiException;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UserTokenValidatorTest {
    @ParameterizedTest
    @ValueSource(ints = {204, 401, 200, 403, 302, 429, 500, 503})
    void verifiesThroughUserAndForwardsOriginalBearer(int upstream) throws Exception {
        var header = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/user/api/users/validate", exchange -> {
            header.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Location", "/must-not-follow");
            exchange.sendResponseHeaders(upstream, -1);
            exchange.close();
        });
        server.start();
        try {
            var validator = new UserTokenValidator("http://127.0.0.1:" + server.getAddress().getPort() + "/user/");
            if (upstream == 204) assertDoesNotThrow(() -> validator.validate("original.jwt.token"));
            else assertEquals(upstream == 401 ? 401 : 503,
                    assertThrows(ApiException.class, () -> validator.validate("original.jwt.token")).getStatus());
            assertEquals("Bearer original.jwt.token", header.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void networkFailureDoesNotAuthorize() throws Exception {
        var server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        int port = server.getLocalPort();
        server.close();
        var validator = new UserTokenValidator("http://127.0.0.1:" + port);
        assertEquals(503, assertThrows(ApiException.class, () -> validator.validate("token")).getStatus());
    }
}
