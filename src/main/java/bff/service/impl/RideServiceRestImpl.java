package bff.service.impl;

import bff.dto.EndRideRequest;
import bff.dto.JsonResponse;
import bff.dto.StartRideRequest;
import bff.service.RideService;
import bff.service.ServiceException;

import java.io.IOException;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RideServiceRestImpl implements RideService {

    private static final String BASE = ServiceUrls.get("ride.service.url", "http://localhost:8084");

    private final HttpClient httpClient = HttpClients.shared();

    @Override
    public JsonResponse start(String jwt, StartRideRequest request) {
        return send("POST", URI.create(BASE + "/api/rides"), jwt, toJson(request));
    }

    @Override public JsonResponse position(String jwt,UUID id,EndRideRequest request) {
        return send("PUT",URI.create(BASE+"/api/rides/"+id+"/position"),jwt,"{\"posX\":"+request.posX()+",\"posY\":"+request.posY()+"}");
    }
    @Override
    public JsonResponse finish(String jwt, UUID rideId, EndRideRequest request) {
        String json = "{\"posX\":" + request.posX() + ",\"posY\":" + request.posY() + "}";
        return send("PUT", URI.create(BASE + "/api/rides/" + rideId + "/finish"), jwt, json);
    }

    @Override
    public JsonResponse pay(String jwt, UUID rideId) {
        return send("POST", URI.create(BASE + "/api/rides/" + rideId + "/pay"), jwt, "{}");
    }

    @Override
    public JsonResponse findByUser(String jwt, UUID userId) {
        return send("GET", URI.create(BASE + "/api/rides?userId=" + userId), jwt, null);
    }

    public JsonResponse rates(String jwt) { return send("GET",URI.create(BASE+"/api/rates"),jwt,null); }

    private String toJson(StartRideRequest request) {
        return "{\"numberPlate\":" + JsonText.quote(request.numberPlate())
                + ",\"rateId\":" + JsonText.quote(request.rateId())
                + ",\"posX\":" + (request.posX() == null ? "null" : request.posX())
                + ",\"posY\":" + (request.posY() == null ? "null" : request.posY()) + "}";
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
            throw new ServiceException(503, "Не удалось связаться с сервисом поездок", e);
        }
    }
}
