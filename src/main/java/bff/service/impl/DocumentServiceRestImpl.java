package bff.service.impl;

import bff.dto.DocumentRequest;
import bff.service.DocumentService;
import bff.service.ServiceException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class DocumentServiceRestImpl implements DocumentService {

    private static final String BASE = ServiceUrls.get("document.service.url", "http://localhost:8085");

    private final HttpClient httpClient = HttpClients.shared();

    @Override
    public byte[] generate(String jwt, DocumentRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(BASE + "/api/documents/generate"))
                .header("Accept", "*/*")
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(request)));
        if (jwt != null && !jwt.isBlank()) {
            builder.header("Authorization", "Bearer " + jwt);
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String text = new String(body, StandardCharsets.UTF_8).trim();
                if (text.isBlank()) {
                    text = "HTTP " + response.statusCode();
                }
                throw new ServiceException(response.statusCode(), text);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/pdf")
                    || body.length < 5 || !new String(body, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
                throw new ServiceException(502, "Сервис документов вернул некорректный PDF");
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(503, "Запрос прерван", e);
        } catch (IOException e) {
            throw new ServiceException(503, "Не удалось связаться с сервисом документов", e);
        }
    }

    private String toJson(DocumentRequest request) {
        return "{\"userId\":" + JsonText.quote(request.userId())
                + ",\"docType\":" + JsonText.quote(request.docType())
                + ",\"sourceId\":" + JsonText.quote(request.sourceId())
                + ",\"from\":" + JsonText.quote(request.from())
                + ",\"to\":" + JsonText.quote(request.to()) + "}";
    }
}
