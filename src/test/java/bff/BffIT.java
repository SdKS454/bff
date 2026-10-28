package bff;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Deploy the packaged WAR, including annotation discovery, filters, JSPs and production libraries. */
class BffIT {
    static final String USER = "550e8400-e29b-41d4-a716-446655440000";
    static final String OTHER = "550e8400-e29b-41d4-a716-446655440001";
    static final String RIDE = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    static final String RATE = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    static HttpServer backend;
    static Tomcat tomcat;
    static String base;
    static final List<Call> calls = new CopyOnWriteArrayList<>();
    static volatile int validateStatus, blockedStatus, rideStatus, loginStatus, documentStatus;
    static volatile String blockedBody, profile, loginBody, documentContentType;
    static volatile int logoutStatus;
    static volatile long documentDelayMillis;
    static volatile byte[] documentBody;
    static volatile String rideState;
    HttpClient client;
    record Call(String method, String path, String query, String authorization, String body) { }

    @BeforeAll static void start() throws Exception {
        backend = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        backend.createContext("/", BffIT::handle);
        backend.start();
        String url = "http://localhost:" + backend.getAddress().getPort();
        for (String service : List.of("user", "scooter", "ride", "document")) System.setProperty(service + ".service.url", url);
        System.setProperty("moderation.panel.url", "https://moderation.example/panel/");
        tomcat = new Tomcat();
        Path tomcatBase = Files.createTempDirectory("bff-tomcat-");
        Files.createDirectories(tomcatBase.resolve("webapps"));
        tomcat.setBaseDir(tomcatBase.toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addWebapp("/scooter-bff", Path.of("target/scooter-bff.war").toAbsolutePath().toString());
        context.setParentClassLoader(BffIT.class.getClassLoader());
        tomcat.start();
        assertTrue(context.getState().isAvailable(), "WAR must deploy");
        base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/scooter-bff";
    }
    @AfterAll static void stop() throws Exception {
        System.clearProperty("moderation.panel.url");
        if (tomcat != null) { tomcat.stop(); tomcat.destroy(); }
        if (backend != null) backend.stop(0);
        assertFalse(Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.isAlive() && t.getName().startsWith("bff-http-")), "HTTP executor must stop on undeploy");
        for (String service : List.of("user", "scooter", "ride", "document")) System.clearProperty(service + ".service.url");
    }
    @BeforeEach void reset() {
        calls.clear();
        validateStatus = 204; blockedStatus = 200; rideStatus = 200; loginStatus = 200; documentStatus = 200;
        blockedBody = "{\"blocked\":false}";
        profile = "{\"id\":\"" + USER + "\",\"login\":\"ivan\",\"role\":\"USER\",\"deleted\":false,\"blocked\":false}";
        loginBody = "{\"accessToken\":\"jwt-test\",\"expiresInSeconds\":3600,\"user\":" + profile + "}";
        documentContentType = "application/pdf";
        documentBody = "%PDF-1.4\nfixture\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        rideState = "ACTIVE";
        logoutStatus = 204; documentDelayMillis = 0;
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    static void handle(HttpExchange e) throws IOException {
        String path = e.getRequestURI().getPath();
        String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(new Call(e.getRequestMethod(), path, e.getRequestURI().getRawQuery(), e.getRequestHeaders().getFirst("Authorization"), body));
        int status = 200;
        String result = "{}";
        if (path.startsWith("/api/admin/scooters")) result="[]";
        else if (path.equals("/api/reviews")) { status = rideStatus; result = "[]"; }
        else if (path.endsWith("/validate")) status = validateStatus;
        else if (path.equals("/api/users/login")) { status = loginStatus; result = loginBody; }
        else if (path.equals("/api/users/logout")) { status = logoutStatus; result = "{\"error\":\"Logout unavailable\"}"; }
        else if (path.equals("/api/users/register")) { status = 201; result = profile; }
        else if (path.equals("/api/users/me")) result = profile;
        else if (path.equals("/api/users/exists")) result = "{\"exists\":true}";
        else if (path.equals("/api/rates")) result="[{\"id\":\"6ba7b810-9dad-11d1-80b4-00c04fd430c8\",\"unlockFee\":50,\"pricePerMinute\":8,\"currency\":\"RUB\"}]";
        else if (path.endsWith("/blocked")) { status = blockedStatus; result = blockedBody; }
        else if(path.equals("/api/documents"))result="[]";
        else if(path.equals("/api/documents/"+RIDE)&&e.getRequestMethod().equals("DELETE"))status=204;
        else if(path.equals("/api/rides/"+RIDE+"/position"))status=204;
        else if (path.equals("/api/documents/generate") && documentStatus == 200) {
            if (documentDelayMillis > 0) {
                try { Thread.sleep(documentDelayMillis); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            e.getResponseHeaders().set("Content-Type", documentContentType);
            e.sendResponseHeaders(200, documentBody.length);
            e.getResponseBody().write(documentBody); e.close(); return;
        } else if (path.equals("/api/documents/generate")) {
            status = documentStatus; result = "{\"error\":\"Документ недоступен\"}";
        } else if (path.startsWith("/api/rides")) {
            status = rideStatus;
            if (rideStatus >= 400) result = "{\"error\":\"Отказ поездки\"}";
            else {
                if (path.endsWith("/finish")) rideState = "FINISHED";
                else if (path.endsWith("/pay")) rideState = "PAID";
                else if (e.getRequestMethod().equals("POST")) { rideState = "ACTIVE"; status = 201; }
                result = "{\"id\":\"" + RIDE + "\",\"status\":\"" + rideState + "\"}";
                if (e.getRequestMethod().equals("GET")) result = "[" + result + "]";
            }
        } else if (path.startsWith("/api/scooters")) result = "[{\"numberPlate\":\"A123BC\",\"status\":\"READY\"}]";
        else { status = 404; result = "{\"error\":\"Not found\"}"; }
        if (status == -1) { e.close(); return; }
        e.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
        e.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) e.getResponseBody().write(bytes);
        e.close();
    }
    HttpResponse<String> request(String method, String path, String json, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path));
        if (token != null) builder.header("Authorization", token);
        if (json != null) builder.header("Content-Type", "application/json");
        builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    Call last(String path) { return calls.stream().filter(c -> c.path().equals(path)).reduce((a,b) -> b).orElseThrow(); }
    boolean called(String path) { return calls.stream().anyMatch(c -> c.path().equals(path)); }
    @Test void scootxPageAndLegacyEntryPointsAreAvailable() throws Exception {
        var response = request("GET", "/", null, null);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("ScootX"));
        assertTrue(response.body().contains("/scooter-bff/frontend/api.js"));
        assertFalse(response.body().contains("demo-storage.js"));
        for (String page : List.of("/login.jsp", "/register.jsp", "/main.jsp")) {
            response = request("GET", page, null, null);
            assertEquals(302, response.statusCode());
            assertTrue(response.headers().firstValue("Location").orElseThrow().startsWith(page.equals("/login.jsp") ? "/scooter-bff/auth/entry" : "/scooter-bff/?"));
        }
    }
    @Test void publicRegistrationDropsClientRole() throws Exception {
        var response = request("POST", "/api/auth/register", "{\"login\":\"ivan\",\"password\":\"secret123\",\"fullName\":\"Иван Петров\",\"birthDate\":\"2000-05-17\",\"role\":\"ADMIN\"}", null);
        assertEquals(201, response.statusCode(), response.body());
        assertFalse(last("/api/users/register").body().contains("role"));
        assertFalse(called("/api/users/validate"));
    }
    @Test void loginCreatesUsableHttpOnlySessionAndLogoutRemovesIt() throws Exception {
        var response = request("POST", "/api/auth/login", "{\"login\":\"ivan\",\"password\":\"secret\"}", null);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"id\":\"" + USER + "\""));
        assertTrue(response.headers().allValues("Set-Cookie").stream().anyMatch(v -> v.contains("HttpOnly")));
        assertEquals("{\"login\":\"ivan\",\"password\":\"secret\"}", last("/api/users/login").body());
        assertEquals(200, request("GET", "/api/users/me", null, null).statusCode());
        assertEquals("Bearer jwt-test", last("/api/users/me").authorization());
        assertEquals(401, request("GET", "/api/users/me", null, "Basic invalid").statusCode());
        assertEquals(401, request("GET", "/api/users/me", null, "Bearer ").statusCode());
        assertEquals(303, request("POST", "/logout", null, null).statusCode());
        assertEquals(401, request("GET", "/api/users/me", null, null).statusCode());
    }
    @Test void logoutRevokesCurrentBearerBeforeClearingSession() throws Exception {
        request("POST", "/api/auth/login", "{\"login\":\"ivan\",\"password\":\"secret\"}", null);
        var response = request("POST", "/api/auth/logout", null, "Bearer explicit-token");
        assertEquals(204, response.statusCode());
        assertEquals("POST", last("/api/users/logout").method());
        assertEquals("Bearer explicit-token", last("/api/users/logout").authorization());
        assertEquals(401, request("GET", "/api/users/me", null, null).statusCode());
    }
    @Test void logoutDoesNotPrevalidateAndHandlesAlreadyRevokedToken() throws Exception {
        validateStatus = 401; logoutStatus = 401;
        assertEquals(204, request("POST", "/api/auth/logout", null, "Bearer expired").statusCode());
        assertFalse(called("/api/users/validate"));
        assertEquals(204, request("POST", "/api/auth/logout", null, null).statusCode());
    }
    @Test void failedRevocationIsNotReportedAsSuccessfulLogout() throws Exception {
        request("POST", "/api/auth/login", "{\"login\":\"ivan\",\"password\":\"secret\"}", null);
        logoutStatus = 503;
        var response = request("POST", "/api/auth/logout", null, null);
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("не подтвердил отзыв"));
        assertEquals(401, request("GET", "/api/users/me", null, null).statusCode());
    }
    @Test void logoutGetIsConfirmationAndNeverRevokes() throws Exception {
        var response = request("GET", "/logout", null, null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("method=\"post\""));
        assertFalse(called("/api/users/logout"));
    }
    @Test void authenticatedFrontendConfigHasTrustedModerationDestination() throws Exception {
        assertEquals(401, request("GET", "/api/config", null, null).statusCode());
        var response = request("GET", "/api/config", null, "Bearer t");
        assertEquals(200, response.statusCode());
        assertEquals("{\"moderationPanelUrl\":\"https://moderation.example/panel/\"}", response.body());
    }
    @Test void usernameQueryIsEncodedOnce() throws Exception {
        assertEquals(200, request("GET", "/api/users/exists?login=a%2Bb%26c", null, null).statusCode());
        assertEquals("login=a%2Bb%26c", last("/api/users/exists").query());
        assertEquals(400, request("GET", "/api/users/exists?login=", null, null).statusCode());
    }
    @Test void anonymousCannotAccessProtectedOrUnknownAuthPaths() throws Exception {
        assertEquals(401, request("GET", "/api/rides", null, null).statusCode());
        assertEquals(401, request("POST", "/api/auth/arbitrary", "{}", null).statusCode());
        assertTrue(calls.isEmpty());
    }
    @Test void invalidExpiredAndUnavailableValidationRemainDistinct() throws Exception {
        validateStatus = 401;
        assertEquals(401, request("GET", "/api/users/me", null, "Bearer expired").statusCode());
        validateStatus = 503;
        assertEquals(503, request("GET", "/api/users/me", null, "Bearer valid").statusCode());
        assertFalse(called("/api/users/me"));
    }
    @Test void connectionFailureIs503() throws Exception {
        validateStatus = -1;
        assertEquals(503, request("GET", "/api/users/me", null, "Bearer t").statusCode());
        assertFalse(called("/api/users/me"));
    }
    @Test void startUsesUuidProfileAndRateAndForwardsOriginalJwt() throws Exception {
        String body = "{\"numberPlate\":\"A123BC\",\"rateId\":\"" + RATE + "\",\"posX\":37.12,\"posY\":55.5}";
        assertEquals(201, request("POST", "/api/rides", body, "Bearer original").statusCode());
        assertEquals("Bearer original", last("/api/users/" + USER + "/blocked").authorization());
        assertEquals(body, last("/api/rides").body());
        assertEquals("Bearer original", last("/api/rides").authorization());
    }
    @Test void fullBffRentalFlowWithSessionAndPdf() throws Exception {
        assertEquals(201, request("POST", "/api/auth/register", "{\"login\":\"ivan\",\"password\":\"secret123\",\"fullName\":\"Иван Петров\",\"birthDate\":\"2000-05-17\"}", null).statusCode());
        assertEquals(200, request("POST", "/api/auth/login", "{\"login\":\"ivan\",\"password\":\"secret\"}", null).statusCode());
        assertEquals(200, request("POST", "/api/scooters/nearest", "{\"posX\":10,\"posY\":20}", null).statusCode());
        assertEquals(201, request("POST", "/api/rides", "{\"numberPlate\":\"A123BC\"}", null).statusCode());
        assertTrue(request("PUT", "/api/rides/" + RIDE + "/finish", "{\"posX\":15,\"posY\":30}", null).body().contains("FINISHED"));
        assertTrue(request("POST", "/api/rides/" + RIDE + "/pay", "{}", null).body().contains("PAID"));
        var pdf = request("POST", "/api/documents/generate", "{\"docType\":\"RIDE_PAYMENT\",\"sourceId\":\"" + RIDE + "\"}", null);
        assertEquals(200, pdf.statusCode(), pdf.body());
        assertEquals("application/pdf", pdf.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(pdf.body().startsWith("%PDF-"));
        assertEquals("no-store", pdf.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(303, request("POST", "/logout", null, null).statusCode());
    }
    @Test void blockedUserCannotStartButMayFinishPayOrRequestDocument() throws Exception {
        blockedBody = "{\"blocked\":true}";
        assertEquals(403, request("POST", "/api/rides", "{\"numberPlate\":\"A\"}", "Bearer t").statusCode());
        assertFalse(called("/api/rides"));
        assertEquals(200, request("PUT", "/api/rides/" + RIDE + "/finish", "{\"posX\":1,\"posY\":2}", "Bearer t").statusCode());
        assertEquals(200, request("POST", "/api/rides/" + RIDE + "/pay", "{}", "Bearer t").statusCode());
        assertEquals(200, request("POST", "/api/documents/generate", "{\"docType\":\"USER_BLOCKED\"}", "Bearer t").statusCode());
    }
    @ParameterizedTest @ValueSource(strings={"", "{}", "{\"blocked\":null}", "{\"blocked\":\"false\"}", "garbage"})
    void malformedBlockedStatusDoesNotAllowRideOrPretendUserIsBanned(String response) throws Exception {
        blockedBody = response;
        assertEquals(502, request("POST", "/api/rides", "{\"numberPlate\":\"A\"}", "Bearer t").statusCode());
        assertFalse(called("/api/rides"));
    }
    @Test void moderationOutageIs503AndNot403() throws Exception {
        blockedStatus = 503; blockedBody = "{\"error\":\"Moderation unavailable\"}";
        assertEquals(503, request("POST", "/api/rides", "{\"numberPlate\":\"A\"}", "Bearer t").statusCode());
        assertFalse(called("/api/rides"));
    }
    @Test void malformedUpstreamUserIs502AndDeletedUserCannotStart() throws Exception {
        profile = "{\"id\":42}";
        assertEquals(502, request("GET", "/api/rides", null, "Bearer t").statusCode());
        profile = "{\"id\":\"" + USER + "\",\"deleted\":true}";
        assertEquals(403, request("POST", "/api/rides", "{\"numberPlate\":\"A\"}", "Bearer t").statusCode());
        assertFalse(called("/api/rides"));
    }
    @ParameterizedTest @ValueSource(strings={"{}", "{\"accessToken\":\"t\",\"expiresInSeconds\":0}", "{\"accessToken\":\"t\",\"expiresInSeconds\":1.5}", "[]"})
    void malformedLoginDoesNotCreateSession(String response) throws Exception {
        loginBody = response;
        assertEquals(502, request("POST", "/api/auth/login", "{\"login\":\"a\",\"password\":\"b\"}", null).statusCode());
        assertEquals(401, request("GET", "/api/users/me", null, null).statusCode());
    }
    @Test void downstreamErrorsKeepStatusAndJsonBody() throws Exception {
        rideStatus = 409;
        var response = request("POST", "/api/rides/" + RIDE + "/pay", "{}", "Bearer t");
        assertEquals(409, response.statusCode());
        assertEquals("{\"error\":\"Отказ поездки\"}", response.body());
        loginStatus = 401; loginBody = "{\"error\":\"Неверный пароль\"}";
        response = request("POST", "/api/auth/login", "{\"login\":\"a\",\"password\":\"b\"}", null);
        assertEquals(401, response.statusCode());
        assertEquals(loginBody, response.body());
    }
    @Test void historyNeverUsesClientSuppliedUserIdAndUnknownPathIs404() throws Exception {
        assertEquals(200, request("GET", "/api/rides?userId=" + OTHER, null, "Bearer t").statusCode());
        assertEquals("userId=" + USER, last("/api/rides").query());
        assertEquals(404, request("GET", "/api/rides/arbitrary", null, "Bearer t").statusCode());
        assertEquals(404, request("PUT", "/api/rides/12/finish", "{}", "Bearer t").statusCode());
        assertEquals(404, request("POST", "/api/rides/not-uuid/pay", "{}", "Bearer t").statusCode());
    }
    @Test void ratesRequireAuthenticationAndForwardOriginalJwt() throws Exception {
        assertEquals(401,request("GET","/api/rates",null,null).statusCode());
        var result=request("GET","/api/rates",null,"Bearer t");
        assertEquals(200,result.statusCode());assertTrue(result.body().contains("pricePerMinute"));
        assertEquals("Bearer t",last("/api/rates").authorization());
    }
    @Test void bodylessDeleteWithoutLengthWorksButChunkedNonJsonStillFails() throws Exception {
        URI uri=URI.create(base);
        try(Socket socket=new Socket(uri.getHost(),uri.getPort())) {
            socket.setSoTimeout(10000);
            String wire="DELETE "+uri.getPath()+"/api/documents/"+RIDE+" HTTP/1.1\r\nHost: "+uri.getHost()+":"+uri.getPort()+"\r\nAuthorization: Bearer t\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(wire.getBytes(StandardCharsets.US_ASCII));
            var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
            assertTrue(reader.readLine().startsWith("HTTP/1.1 204"));
        }
        assertEquals("DELETE",last("/api/documents/"+RIDE).method());
        var chunked=HttpRequest.newBuilder(URI.create(base+"/api/auth/login"))
            .header("Content-Type","text/plain").POST(HttpRequest.BodyPublishers.ofInputStream(
                ()->new ByteArrayInputStream("{}".getBytes(StandardCharsets.US_ASCII)))).build();
        assertEquals(415,client.send(chunked,HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test void documentHistoryPaginationAndTokenAreForwarded() throws Exception {
        assertEquals(200,request("GET","/api/documents?limit=1&offset=2",null,"Bearer t").statusCode());
        assertEquals("limit=1&offset=2",last("/api/documents").query());
        assertEquals("Bearer t",last("/api/documents").authorization());
        assertEquals(400,request("GET","/api/documents?limit=101",null,"Bearer t").statusCode());
        assertEquals(404,request("GET","/api/documents/not-a-uuid",null,"Bearer t").statusCode());
    }
    @Test void positionUsesOriginalTokenAndReturnsNoContent() throws Exception {
        assertEquals(204,request("PUT","/api/rides/"+RIDE+"/position","{\"posX\":10.25,\"posY\":11.75}","Bearer t").statusCode());
        assertEquals("Bearer t",last("/api/rides/"+RIDE+"/position").authorization());
        assertEquals(400,request("PUT","/api/rides/"+RIDE+"/position","{\"posX\":10}","Bearer t").statusCode());
    }
    @Test void documentIdentityComesFromProfileAndLegacyTypeIsMapped() throws Exception {
        var response = request("POST", "/api/documents/generate", "{\"userId\":\"" + OTHER + "\",\"docType\":\"RECEIPT\",\"sourceId\":\"" + RIDE + "\",\"status\":\"PAID\"}", "Bearer t");
        assertEquals(200, response.statusCode(), response.body());
        String body = last("/api/documents/generate").body();
        assertTrue(body.contains(USER)); assertFalse(body.contains(OTHER));
        assertTrue(body.contains("RIDE_PAYMENT")); assertFalse(body.contains("status"));
        assertEquals("Bearer t", last("/api/documents/generate").authorization());
    }
    @ParameterizedTest @ValueSource(strings={"{}", "{\"docType\":\"OTHER\"}", "{\"docType\":\"RIDE_PAYMENT\"}", "{\"docType\":\"USER_HISTORY\",\"from\":\"2026-10-18\",\"to\":\"2026-10-01\"}", "{\"docType\":\"USER_HISTORY\",\"from\":\"bad\",\"to\":\"2026-10-01\"}"})
    void rejectsInvalidDocumentBeforeDownstream(String body) throws Exception {
        assertEquals(400, request("POST", "/api/documents/generate", body, "Bearer t").statusCode());
        assertFalse(called("/api/documents/generate"));
    }
    @Test void documentErrorKeepsStatusAndBody() throws Exception {
        documentStatus = 403;
        var response = request("POST", "/api/documents/generate", "{\"docType\":\"RIDE_PAYMENT\",\"sourceId\":\"" + RIDE + "\"}", "Bearer t");
        assertEquals(403, response.statusCode());
        assertEquals("{\"error\":\"Документ недоступен\"}", response.body());
    }
    @Test void validHistoryPeriodIsForwardedWithAuthenticatedUser() throws Exception {
        var response = request("POST", "/api/documents/generate", "{\"docType\":\"USER_HISTORY\",\"from\":\"2026-10-01\",\"to\":\"2026-10-18\"}", "Bearer t");
        assertEquals(200, response.statusCode());
        String body = last("/api/documents/generate").body();
        assertTrue(body.contains(USER));
        assertTrue(body.contains("2026-10-01"));
        assertTrue(body.contains("2026-10-18"));
        assertTrue(body.contains("\"sourceId\":null"));
    }
    @Test void malformedRequestDoesNotReachUserOrRide() throws Exception {
        assertEquals(400, request("POST", "/api/auth/login", "{}", null).statusCode());
        assertEquals(400, request("POST", "/api/auth/register", "{broken", null).statusCode());
        assertEquals(400, request("POST", "/api/rides", "{\"numberPlate\":\"A\",\"rateId\":42}", "Bearer t").statusCode());
        assertFalse(called("/api/users/login"));
        assertFalse(called("/api/users/register"));
        assertFalse(called("/api/rides"));
    }
    @Test void blockedUserCanLoginWithBlockedFlag() throws Exception {
        loginBody = loginBody.replace("\"blocked\":false", "\"blocked\":true");
        var response = request("POST", "/api/auth/login", "{\"login\":\"a\",\"password\":\"b\"}", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"blocked\":true"));
    }
    @Test void rideTransportFailureIs503() throws Exception {
        rideStatus = -1;
        assertEquals(503, request("POST", "/api/rides/" + RIDE + "/pay", "{}", "Bearer t").statusCode());
    }
    @Test void successfulJsonResponseCannotMasqueradeAsPdf() throws Exception {
        documentContentType = "application/json"; documentBody = "{}".getBytes(StandardCharsets.UTF_8);
        assertEquals(502, request("POST", "/api/documents/generate", "{\"docType\":\"USER_BLOCKED\"}", "Bearer t").statusCode());
    }
    @Test void nearestRequiresNumbersAndFinishRequiresBothCoordinates() throws Exception {
        assertEquals(400, request("POST", "/api/scooters/nearest", "{\"posX\":null,\"posY\":2}", "Bearer t").statusCode());
        assertEquals(400, request("POST", "/api/scooters/nearest", "{\"posX\":\"1\",\"posY\":2}", "Bearer t").statusCode());
        assertEquals(405, request("GET", "/api/scooters/nearest", null, "Bearer t").statusCode());
        assertEquals(400, request("PUT", "/api/rides/" + RIDE + "/finish", "{}", "Bearer t").statusCode());
        assertFalse(called("/api/scooters/nearest"));
        assertFalse(called("/api/rides/" + RIDE + "/finish"));
    }
    @Test void platesWithPlusAndPercentAreNotDecodedTwice() throws Exception {
        assertEquals(200, request("GET", "/api/scooters/A%2BB%2520", null, "Bearer t").statusCode());
        assertEquals("A+B%20", last("/api/scooters/A+B%20").path().substring("/api/scooters/".length()));
        assertEquals(200, request("GET", "/api/scooters?status=READY", null, "Bearer t").statusCode());
        assertEquals("status=READY", last("/api/scooters").query());
    }
    @Test void crossSiteAndNonJsonWritesAreRejected() throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + "/api/auth/login")).header("Content-Type", "application/json")
                .header("Origin", "https://attacker.example").POST(HttpRequest.BodyPublishers.ofString("{}"));
        assertEquals(403, client.send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        builder = HttpRequest.newBuilder(URI.create(base + "/api/auth/login")).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
        assertEquals(415, client.send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertFalse(called("/api/users/login"));
    }
    @Test void publicReviewsNeverRequireOrForwardAUserToken() throws Exception {
        validateStatus = 503;
        var response = request("GET", "/api/reviews?limit=2&offset=0", null, null);
        assertEquals(200, response.statusCode()); assertEquals("[]", response.body());
        assertFalse(called("/api/users/validate"));
        assertNull(last("/api/reviews").authorization());
        assertEquals("limit=2&offset=0", last("/api/reviews").query());
    }
    @Test void publicReviewPagingIsBoundedAndErrorsAreVisible() throws Exception {
        for (String query : List.of("limit=0", "limit=101", "offset=-1", "offset=2147483648", "limit=x"))
            assertEquals(400, request("GET", "/api/reviews?" + query, null, null).statusCode());
        assertFalse(called("/api/reviews"));
        rideStatus = 503;
        assertEquals(503, request("GET", "/api/reviews", null, null).statusCode());
        assertEquals(401, request("POST", "/api/reviews", "{}", null).statusCode());
        assertEquals(401, request("GET", "/api/reviews/private", null, null).statusCode());
    }
    @Test void loginEntryPreservesUserAndAdminSessionsWithoutRevokingTokens() throws Exception {
        assertEquals("/scooter-bff/?auth=login", request("GET", "/auth/entry", null, null).headers().firstValue("Location").orElseThrow());
        request("POST", "/api/auth/login", "{\"login\":\"ivan\",\"password\":\"password\"}", null);
        assertEquals("/scooter-bff/?account=profile", request("GET", "/auth/entry", null, null).headers().firstValue("Location").orElseThrow());
        assertFalse(called("/api/users/logout"));
        profile = profile.replace("USER", "ADMIN");
        assertEquals("https://moderation.example/panel/#accessToken=jwt-test", request("GET", "/auth/entry", null, null).headers().firstValue("Location").orElseThrow());
        assertFalse(called("/api/users/logout"));
    }
    @Test void addressBarApiNavigationIsHtmlButApiClientsKeepTheirPermissions() throws Exception {
        var browser = HttpRequest.newBuilder(URI.create(base + "/api/users/me"))
                .header("Accept", "text/html").GET().build();
        var response = client.send(browser, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode()); assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
        assertTrue(response.body().contains("Служебный адрес"));
        assertFalse(called("/api/users/me"));
        assertEquals(401, request("GET", "/api/users/me", null, null).statusCode());
        assertEquals(200, request("GET", "/api/users/me", null, "Bearer t").statusCode());
    }
    @Test void firstPdfMayTakeLongerThanTenSecondsWithoutBeingReportedUnavailable() throws Exception {
        documentDelayMillis = 11000;
        var response = request("POST", "/api/documents/generate", "{\"docType\":\"USER_HISTORY\",\"from\":\"2026-10-01\",\"to\":\"2026-10-28\"}", "Bearer t");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/pdf"));
        assertTrue(response.body().startsWith("%PDF-"));
    }
    @Test void fleetAdminRequiresAuthenticationAndCurrentAdminRole() throws Exception {
        assertEquals(401, request("GET", "/api/admin/scooters", null, null).statusCode());
        assertEquals(403, request("GET", "/api/admin/scooters", null, "Bearer user").statusCode());
        assertFalse(called("/api/admin/scooters"));
        assertEquals(403, request("GET", "/admin/scooters", null, "Bearer user").statusCode());
    }
    @Test void fleetAdminForwardsTokenAndWritesOnlyForAdmin() throws Exception {
        profile=profile.replace("USER", "ADMIN");
        assertEquals(200, request("GET", "/api/admin/scooters", null, "Bearer admin").statusCode());
        assertEquals("Bearer admin", last("/api/admin/scooters").authorization());
        String body="{\"numberPlate\":\"TEST\",\"posX\":37.6,\"posY\":55.7}";
        assertEquals(200, request("POST", "/api/admin/scooters", body, "Bearer admin").statusCode());
        assertEquals(body,last("/api/admin/scooters").body());
        assertEquals(405,request("GET","/api/admin/scooters/TEST/charge",null,"Bearer admin").statusCode());
        assertEquals(200,request("POST","/api/admin/scooters/TEST/charge",null,"Bearer admin").statusCode());
        assertEquals("POST",last("/api/admin/scooters/TEST/charge").method());
        assertEquals(200,request("DELETE","/api/admin/scooters/TEST",null,"Bearer admin").statusCode());
        assertEquals("DELETE",last("/api/admin/scooters/TEST").method());
    }
    @Test void fleetAdminPageCompilesAndDoesNotExposeToken() throws Exception {
        assertEquals(302,request("GET","/admin/scooters",null,null).statusCode());
        profile=profile.replace("USER", "ADMIN");
        var result=request("GET","/admin/scooters",null,"Bearer secret-token");
        assertEquals(200,result.statusCode(),result.body());
        assertTrue(result.body().contains("Управление самокатами"));
        assertTrue(result.body().contains("/scooter-bff/frontend/admin-scooters.js"));
        assertFalse(result.body().contains("secret-token"));
        assertEquals(404,request("GET","/WEB-INF/admin-scooters.jsp",null,"Bearer admin").statusCode());
    }
}
