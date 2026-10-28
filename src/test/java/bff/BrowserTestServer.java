package bff;

import bff.service.impl.JsonText;
import com.sun.net.httpserver.*;
import jakarta.json.*;
import org.apache.catalina.startup.Tomcat;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test-only downstream fixtures. The browser still uses HTTP, servlet filters and the packaged BFF WAR. */
public final class BrowserTestServer {
    static final String USER = "550e8400-e29b-41d4-a716-446655440000";
    static final String RIDE = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    static volatile JsonObject fixture = Json.createObjectBuilder().build();
    static volatile boolean started, revoked;
    static volatile String reviewJson;
    static volatile String rideState = "ACTIVE", name = "Иван Петров", login = "ivan";
    static final List<JsonObject> calls = new CopyOnWriteArrayList<>();
    static int status(String key, int defaultValue) { return fixture.getInt(key, defaultValue); }
    static String plate() { return fixture.getString("plate", "A123BC"); }
    static String user() {
        return "{\"id\":\"" + USER + "\",\"login\":" + JsonText.quote(login)
                + ",\"fullName\":" + JsonText.quote(name) + ",\"birthDate\":\"2000-05-17\",\"role\":"
                + JsonText.quote(fixture.getString("role", "USER")) + ",\"deleted\":false,\"blocked\":" + fixture.getBoolean("blocked", false) + "}";
    }
    static String scooter() {
        return "{\"numberPlate\":" + JsonText.quote(plate()) + ",\"status\":\"READY\""
                + (fixture.getBoolean("missingFields", false) ? "" : ",\"charge\":87,\"posX\":10,\"posY\":24,\"distance\":1.5") + "}";
    }
    static String ride() {
        return "{\"id\":\"" + RIDE + "\",\"userId\":\"" + USER + "\",\"numberPlate\":" + JsonText.quote(plate())
                + ",\"startPosX\":10,\"startPosY\":24,\"status\":" + JsonText.quote(rideState) + ",\"startedAt\":" + JsonText.quote(Instant.now().minusSeconds(120).toString())
                + (rideState.equals("ACTIVE") ? "" : ",\"finishedAt\":" + JsonText.quote(Instant.now().toString()) + ",\"durationSeconds\":120,\"amount\":123.45,\"currency\":\"RUB\"") + (reviewJson == null ? "" : ",\"review\":" + reviewJson) + "}";
    }
    static void send(HttpExchange e, int code, String type, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", type);
        e.sendResponseHeaders(code, code == 204 ? -1 : data.length);
        if (code != 204) e.getResponseBody().write(data);
        e.close();
    }
    static void handle(HttpExchange e) throws IOException {
        String path = e.getRequestURI().getPath(), method = e.getRequestMethod();
        String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (path.equals("/__fixture")) {
            fixture = JsonText.object(body); started = false; reviewJson = null; revoked = false; rideState = "ACTIVE"; name = "Иван Петров"; login = "ivan"; calls.clear();
            send(e, 204, "application/json", ""); return;
        }
        if (path.equals("/__patch")) {
            var patch = JsonText.object(body); var merged = Json.createObjectBuilder(fixture);
            patch.forEach(merged::add); fixture = merged.build();
            send(e, 204, "application/json", ""); return;
        }
        if (path.equals("/__calls")) {
            var items = Json.createArrayBuilder(); calls.forEach(items::add);
            send(e, 200, "application/json", items.build().toString()); return;
        }
        if (path.equals("/admin/users.html")) { send(e, 200, "text/html", "<!doctype html><title>Moderation</title><h1>Moderation test panel</h1>"); return; }
        calls.add(Json.createObjectBuilder().add("method", method).add("path", path).build());
        int code = 200; String result = "{}";
        if (path.equals("/api/users/validate")) code = revoked ? 401 : status("validateStatus", 204);
        else if (path.equals("/api/users/register")) {
            code = status("registerStatus", 201);
            if (code == 201) { var input = JsonText.object(body); name = input.getString("fullName"); login = input.getString("login"); result = user(); }
        } else if (path.equals("/api/users/login")) {
            code = status("loginStatus", 200); revoked = false;
            result = "{\"accessToken\":\"browser-test-token\",\"expiresInSeconds\":3600,\"user\":" + user() + "}";
        } else if (path.equals("/api/users/logout")) { code = status("logoutStatus", 204); if (code == 204) revoked = true; }
        else if (path.equals("/api/users/me")) { code = status("profileStatus", 200); result = user(); }
        else if (path.endsWith("/blocked")) result = "{\"blocked\":" + fixture.getBoolean("blocked", false) + "}";
        else if (path.equals("/api/rates")) { code = status("ratesStatus",200); result="[{\"id\":\"6ba7b810-9dad-11d1-80b4-00c04fd430c8\",\"default\":true,\"name\":\"Учебный\",\"unlockFee\":50,\"pricePerMinute\":8,\"currency\":\"RUB\",\"paymentMode\":\"EDUCATIONAL\"}]"; }
        else if (path.startsWith("/api/scooters")) {
            code = status("catalogStatus", 200);
            result = path.equals("/api/scooters") || path.endsWith("/nearest") ? "[" + scooter() + "]" : scooter();
        } else if (path.equals("/api/rides")) {
            code = status("ridesStatus", 200);
            if (method.equals("POST") && code == 200) { code = 201; started = true; rideState = "ACTIVE"; result = ride(); }
            else result = started ? "[" + ride() + "]" : "[]";
        } else if (path.endsWith("/finish")) { code = status("finishStatus", 200); if (code == 200) rideState = "FINISHED"; result = ride(); }
        else if (path.endsWith("/review")) { code = status("reviewStatus",200); if (code == 200) reviewJson = body; result = ride(); }
        else if (path.endsWith("/pay")) { code = status("payStatus", 200); if (code == 200) rideState = "PAID"; result = ride(); }
        else if(path.equals("/api/documents")){result="[]";}
        else if (path.equals("/api/documents/generate")) {
            code = status("documentStatus", 200);
            if (code == 200) { send(e, code, "application/pdf", "%PDF-1.4\nfixture\n%%EOF"); return; }
        } else code = 404;
        if (code >= 400) result = "{\"error\":" + JsonText.quote(code == 409 ? "Логин уже занят" : "Сервис временно недоступен: " + code) + "}";
        send(e, code, "application/json; charset=UTF-8", result);
    }
    public static void main(String[] args) throws Exception {
        HttpServer fixtureServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fixtureServer.createContext("/", BrowserTestServer::handle); fixtureServer.start();
        String fixtureBase = "http://127.0.0.1:" + fixtureServer.getAddress().getPort();
        for (String service : List.of("user", "scooter", "ride", "document")) System.setProperty(service + ".service.url", fixtureBase);
        System.setProperty("moderation.panel.url", fixtureBase + "/admin/users.html");
        Tomcat tomcat = new Tomcat();
        Path base = Files.createTempDirectory("scootx-browser-"); Files.createDirectories(base.resolve("webapps"));
        tomcat.setBaseDir(base.toString()); tomcat.setPort(0); tomcat.getConnector().setProperty("address", "127.0.0.1");
        String contextPath = System.getProperty("bff.test.context", "/scooter-bff");
        var context = tomcat.addWebapp(contextPath, Path.of("target/scooter-bff.war").toAbsolutePath().toString());
        context.setParentClassLoader(BrowserTestServer.class.getClassLoader()); tomcat.start();
        if (!context.getState().isAvailable()) throw new IllegalStateException("BFF WAR failed to deploy");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { tomcat.stop(); tomcat.destroy(); } catch (Exception ignored) { }
            fixtureServer.stop(0);
        }));
        System.out.println("SCOOTX_TEST_URL=http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + contextPath + "/");
        System.out.println("SCOOTX_FIXTURE_URL=" + fixtureBase); System.out.flush();
        tomcat.getServer().await();
    }
}
