package bff.service.impl;

import bff.dto.JsonResponse;
import bff.dto.NearestScooterRequest;
import bff.service.ScooterService;
import bff.service.ServiceException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ScooterServiceRestImpl implements ScooterService {

    private static final String BASE = ServiceUrls.get("scooter.service.url", "http://localhost:8083");

    private final HttpClient httpClient = HttpClients.shared();

    @Override
    public JsonResponse list(String jwt, boolean readyOnly) {
        String url = BASE + "/api/scooters" + (readyOnly ? "?status=READY" : "");
        return send("GET", URI.create(url), jwt, null);
    }

    @Override
    public JsonResponse nearest(String jwt, NearestScooterRequest request) {
        String json = "{\"posX\":" + request.posX() + ",\"posY\":" + request.posY() + "}";
        return send("POST", URI.create(BASE + "/api/scooters/nearest"), jwt, json);
    }

    @Override
    public JsonResponse findByNumberPlate(String jwt, String numberPlate) {
        String plate = URLEncoder.encode(numberPlate, StandardCharsets.UTF_8).replace("+", "%20");
        return send("GET", URI.create(BASE + "/api/scooters/" + plate), jwt, null);
    }

    public JsonResponse admin(String method, String path, String jwt, String json) {
        if (!path.matches("(?:/[A-Za-z0-9_.-]{1,64}(?:/charge)?)?"))
            throw new ServiceException(404, "Путь не найден");
        return send(method, URI.create(BASE + "/api/admin/scooters" + path), jwt, json);
    }

    private JsonResponse send(String method, URI uri, String jwt, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10));
        if (jwt != null && !jwt.isBlank()) {
            builder.header("Authorization", "Bearer " + jwt);
        }
        if (json != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            return new JsonResponse(response.statusCode(), body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(503, "Запрос прерван", e);
        } catch (IOException e) {
            throw new ServiceException(503, "Не удалось связаться с сервисом самокатов", e);
        }
    }
}
