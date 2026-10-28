package ru.itis.service;

import ru.itis.config.DatabaseConfig;
import ru.itis.exception.ApiException;
import ru.itis.model.Rate;
import ru.itis.model.RideEntity;
import ru.itis.util.Json;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class RideIntegrationService {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final DatabaseConfig db = new DatabaseConfig();

    public Rate getRate(UUID id) {
        try (var c = db.getConnection(); var q = c.prepareStatement("SELECT * FROM rates WHERE active AND " + (id == null ? "is_default" : "id=?"))) {
            if (id != null) q.setObject(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new ApiException(404, "Тариф не найден");
                return new Rate(r.getObject("id", UUID.class), r.getBigDecimal("unlock_fee"), r.getBigDecimal("price_per_minute"), r.getString("currency"));
            }
        } catch (java.sql.SQLException e) {
            throw new ApiException(503, "Хранилище тарифов недоступно");
        }
    }

    public List<Map<String, Object>> rates() {
        try (var c = db.getConnection(); var q = c.prepareStatement("SELECT * FROM rates WHERE active ORDER BY is_default DESC,id"); var r = q.executeQuery()) {
            var result = new ArrayList<Map<String, Object>>();
            while (r.next())
                result.add(Map.of("id", r.getObject("id").toString(), "name", r.getString("name"), "unlockFee", r.getBigDecimal("unlock_fee"), "pricePerMinute", r.getBigDecimal("price_per_minute"), "currency", r.getString("currency"), "default", r.getBoolean("is_default"), "paymentMode", paymentMode()));
            return result;
        } catch (java.sql.SQLException e) {
            throw new ApiException(503, "Хранилище тарифов недоступно");
        }
    }

    public String paymentMode() {
        return "educational".equalsIgnoreCase(System.getenv("PAYMENT_MODE")) ? "EDUCATIONAL" : "DISABLED";
    }

    public BigDecimal calculatePrice(RideEntity ride, long seconds) {
        long minutes = Math.max(1, Math.addExact(seconds, 59) / 60);
        BigDecimal amount = ride.getUnlockFee().add(ride.getPricePerMinute().multiply(BigDecimal.valueOf(minutes))).setScale(2, RoundingMode.HALF_UP);
        if (amount.precision() - amount.scale() > 12) throw new ApiException(409, "Слишком большая сумма поездки");
        return amount;
    }

    public void processPayment(RideEntity ride) {
        if (!paymentMode().equals("EDUCATIONAL")) throw new ApiException(503, "Платёжный провайдер не настроен");
    }

    public void reserve(RideEntity ride) {
        call(ride, "reserve", Map.of("rideId", ride.getId().toString(), "userId", ride.getUserId().toString()));
    }

    public void position(RideEntity ride, double x, double y) {
        call(ride, "position", Map.of("rideId", ride.getId().toString(), "posX", x, "posY", y));
    }

    public void release(RideEntity ride, boolean finished) {
        Map<String, Object> body = new HashMap<>();
        body.put("rideId", ride.getId().toString());
        if (finished) {
            body.put("posX", ride.getEndPosX());
            body.put("posY", ride.getEndPosY());
        }
        call(ride, "release", body);
    }

    private void call(RideEntity ride, String action, Map<String, Object> body) {
        String key = System.getenv("SERVICE_API_KEY");
        if (key == null || key.isBlank()) throw new ApiException(503, "Не настроен ключ интеграции");
        String base = System.getenv().getOrDefault("SCOOTER_SERVICE_URL", "http://localhost:8083").replaceAll("/+$", "");
        String plate = URLEncoder.encode(ride.getNumberPlate(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        try {
            var request = HttpRequest.newBuilder(URI.create(base + "/api/scooters/" + plate + "/" + action)).timeout(Duration.ofSeconds(3)).header("X-Service-Key", key).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body))).build();
            int code = CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (code == 200) return;
            if (code == 404 || code == 409)
                throw new ApiException(code, code == 404 ? "Самокат не найден" : "Самокат недоступен или состояние изменилось");
            throw new ApiException(503, "Сервис самокатов отклонил запрос");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "Запрос прерван");
        } catch (IOException e) {
            throw new ApiException(503, "Сервис самокатов недоступен");
        }
    }
}
