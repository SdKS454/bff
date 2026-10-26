package ru.kicksharing.documents.service;

import org.thymeleaf.context.Context;
import ru.kicksharing.documents.client.ModerationClient;
import ru.kicksharing.documents.client.RideClient;
import ru.kicksharing.documents.client.UserClient;
import ru.kicksharing.documents.dto.external.ModerationResponse;
import ru.kicksharing.documents.dto.external.RideResponse;
import ru.kicksharing.documents.dto.external.UserResponse;
import ru.kicksharing.documents.dto.internal.RidePaymentDto;
import ru.kicksharing.documents.dto.internal.UserBlockedDto;
import ru.kicksharing.documents.dto.internal.UserHistoryDto;
import ru.kicksharing.documents.dto.request.GenerateDocumentRequest;
import ru.kicksharing.documents.entity.Document;
import ru.kicksharing.documents.exception.ApiException;
import ru.kicksharing.documents.repository.DocumentRepository;
import ru.kicksharing.documents.util.JwtUtil;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DocumentService {

    private final DocumentRepository repository;
    private final UserClient userClient;
    private final RideClient rideClient;
    private final ModerationClient moderationClient;
    private final PdfService pdfService;

    public DocumentService() {
        this(new DocumentRepository(), new UserClient(), new RideClient(), new ModerationClient(), new PdfService());
    }

    DocumentService(DocumentRepository repository, UserClient userClient, RideClient rideClient,
                    ModerationClient moderationClient, PdfService pdfService) {
        this.repository = repository; this.userClient = userClient; this.rideClient = rideClient;
        this.moderationClient = moderationClient; this.pdfService = pdfService;
    }

    public byte[] generate(GenerateDocumentRequest req, String token) {
        // 1. Проверка безопасности: userId из тела == userId из токена
        if (req == null || req.getUserId() == null || req.getDocType() == null)
            throw ApiException.badRequest("userId и docType обязательны");
        UUID tokenUserId = JwtUtil.extractUserId(token);
        if (!tokenUserId.equals(req.getUserId())) {
            throw ApiException.forbidden("Нельзя запросить документ другого пользователя");
        }

        // Downstream clients prepend Bearer themselves; pass only the raw JWT.
        String downstreamJwt = token.substring(7).trim();
        userClient.validate(downstreamJwt);
        String docType = normalizeDocType(req.getDocType());

        if (!List.of("RIDE_PAYMENT", "USER_BLOCKED", "USER_HISTORY").contains(docType))
            throw ApiException.badRequest("Неизвестный docType");
        if ("RIDE_PAYMENT".equals(docType) && req.getSourceId() == null)
            throw ApiException.badRequest("sourceId обязателен для RIDE_PAYMENT");
        if ("USER_HISTORY".equals(docType) && (req.getFrom() == null || req.getTo() == null || req.getFrom().isAfter(req.getTo())))
            throw ApiException.badRequest("Некорректный период from/to");
        if (!"RIDE_PAYMENT".equals(docType) && req.getSourceId() != null)
            throw ApiException.badRequest("sourceId нужен только для RIDE_PAYMENT");

        // 2. Создаем запись в БД со статусом PENDING
        Document document = new Document(req.getUserId(), docType, req.getSourceId(), req.getFrom(), req.getTo());
        repository.save(document);

        try {
            // 3. Маршрутизация по типу
            byte[] pdf = switch (docType) {
                case "RIDE_PAYMENT" -> buildRidePayment(req, downstreamJwt);
                case "USER_BLOCKED" -> buildUserBlocked(req, downstreamJwt);
                case "USER_HISTORY" -> buildUserHistory(req, downstreamJwt);
                default -> throw ApiException.badRequest("Неизвестный docType: " + docType);
            };

            // 4. Обновляем статус на GENERATED
            repository.updateStatus(document.getId(), "GENERATED", OffsetDateTime.now());
            return pdf;

        } catch (Exception e) {
            // При любой ошибке помечаем FAILED
            try { repository.updateStatus(document.getId(), "FAILED", null); }
            catch (Exception markingFailure) { e.addSuppressed(markingFailure); }
            throw e;
        }
    }

    // BFF может присылать RECEIPT — приводим к нашему RIDE_PAYMENT
    private String normalizeDocType(String docType) {
        if ("RECEIPT".equals(docType)) return "RIDE_PAYMENT";
        return docType;
    }

    private byte[] buildRidePayment(GenerateDocumentRequest req, String token) {
        if (req.getSourceId() == null) throw ApiException.badRequest("sourceId обязателен для RIDE_PAYMENT");

        RideResponse ride = rideClient.getRide(req.getSourceId().toString(), token);
        if (!ru.kicksharing.documents.client.ServiceHttp.sameId(ride.getUserId(), req.getUserId().toString())) throw ApiException.forbidden("Чужая поездка");
        if (!"PAID".equals(ride.getStatus())) throw ApiException.conflict("Поездка не оплачена");

        UserResponse user = userClient.getUser(req.getUserId().toString(), token);

        RidePaymentDto dto = new RidePaymentDto();
        dto.setRideId(ride.getId());
        dto.setNumberPlate(ride.getNumberPlate());
        dto.setStartedAt(ride.getStartedAt());
        dto.setFinishedAt(ride.getFinishedAt());
        dto.setAmount(ride.getAmount());
        dto.setCurrency(ride.getCurrency());
        dto.setPaidAt(ride.getPaidAt());
        dto.setPaymentMode(ride.getPaymentMode());
        dto.setUserFullName(user.getFullName());
        dto.setUserLogin(user.getLogin());

        Context ctx = new Context();
        ctx.setVariable("doc", dto);
        return pdfService.generatePdf("ride-payment", ctx);
    }

    private byte[] buildUserBlocked(GenerateDocumentRequest req, String token) {
        ModerationResponse status = moderationClient.getStatus(req.getUserId().toString(), token);
        if (!status.isBlocked()) throw ApiException.conflict("Пользователь не заблокирован");

        UserResponse user = userClient.getUser(req.getUserId().toString(), token);

        UserBlockedDto dto = new UserBlockedDto();
        dto.setUserFullName(user.getFullName());
        dto.setUserLogin(user.getLogin());
        dto.setReason(status.getReason());
        dto.setBlockedAt(status.getBlockedAt());
        dto.setBlockedUntil(status.getBlockedUntil());

        Context ctx = new Context();
        ctx.setVariable("doc", dto);
        return pdfService.generatePdf("user-blocked", ctx);
    }

    private byte[] buildUserHistory(GenerateDocumentRequest req, String token) {
        if (req.getFrom() == null || req.getTo() == null) {
            throw ApiException.badRequest("from и to обязательны для USER_HISTORY");
        }
        UserResponse user = userClient.getUser(req.getUserId().toString(), token);
        List<RideResponse> rides = rideClient.getUserRides(req.getUserId().toString(), token);

        // Фильтруем по периоду
        List<RidePaymentDto> filtered = rides.stream()
                .filter(r -> req.getUserId().toString().equals(r.getUserId()))
                .filter(r -> r.getStartedAt() != null)
                .filter(r -> {
                    var d = r.getStartedAt().atZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDate();
                    return !d.isBefore(req.getFrom()) && !d.isAfter(req.getTo());
                })
                .map(r -> {
                    RidePaymentDto dto = new RidePaymentDto();
                    dto.setRideId(r.getId());
                    dto.setNumberPlate(r.getNumberPlate());
                    dto.setStartedAt(r.getStartedAt());
                    dto.setFinishedAt(r.getFinishedAt());
                    dto.setAmount(r.getAmount());
                    dto.setCurrency(r.getCurrency());
                    dto.setPaidAt(r.getPaidAt());
                    dto.setPaymentMode(r.getPaymentMode());
                    return dto;
                })
                .collect(Collectors.toList());

        UserHistoryDto dto = new UserHistoryDto();
        dto.setUserFullName(user.getFullName());
        dto.setUserLogin(user.getLogin());
        dto.setFrom(req.getFrom());
        dto.setTo(req.getTo());
        dto.setRides(filtered);

        Context ctx = new Context();
        ctx.setVariable("doc", dto);
        return pdfService.generatePdf("user-history", ctx);
    }
}