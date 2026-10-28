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
        this.repository = repository;
        this.userClient = userClient;
        this.rideClient = rideClient;
        this.moderationClient = moderationClient;
        this.pdfService = pdfService;
    }

    public byte[] generate(GenerateDocumentRequest req, String token) {
        if (req == null || req.getUserId() == null || req.getDocType() == null)
            throw ApiException.badRequest("userId и docType обязательны");
        UUID tokenUserId = JwtUtil.extractUserId(token);
        if (!tokenUserId.equals(req.getUserId())) {
            throw ApiException.forbidden("Нельзя запросить документ другого пользователя");
        }

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

        Document document = new Document(req.getUserId(), docType, req.getSourceId(), req.getFrom(), req.getTo());
        repository.save(document);

        try {
            Object data = buildData(req, downstreamJwt, docType);
            byte[] pdf = renderData(docType, data);

            repository.markGenerated(document.getId(), ru.kicksharing.documents.util.JsonUtil.toJson(data), OffsetDateTime.now());
            return pdf;

        } catch (Exception e) {
            try {
                repository.updateStatus(document.getId(), "FAILED", null);
            } catch (Exception markingFailure) {
                e.addSuppressed(markingFailure);
            }
            throw e;
        }
    }

    private Object buildData(GenerateDocumentRequest req, String jwt, String type) {
        return switch (type) {
            case "RIDE_PAYMENT" -> buildRidePayment(req, jwt);
            case "USER_BLOCKED" -> buildUserBlocked(req, jwt);
            case "USER_HISTORY" -> buildUserHistory(req, jwt);
            default -> throw ApiException.badRequest("Неизвестный docType");
        };
    }

    private byte[] renderData(String type, Object data) {
        Context ctx = new Context();
        ctx.setVariable("doc", data);
        String template = switch (type) {
            case "RIDE_PAYMENT" -> "ride-payment";
            case "USER_BLOCKED" -> "user-blocked";
            case "USER_HISTORY" -> "user-history";
            default -> throw ApiException.badRequest("Неизвестный docType");
        };
        return pdfService.generatePdf(template, ctx);
    }

    private UUID actor(String auth) {
        UUID id = JwtUtil.extractUserId(auth);
        userClient.validate(auth.substring(7).trim());
        return id;
    }

    private Document owned(UUID id, UUID owner, boolean includeDeleted) {
        Document document = repository.findById(id, includeDeleted).orElseThrow(() -> ApiException.notFound("Документ не найден"));
        if (!owner.equals(document.getUserId())) throw ApiException.forbidden("Чужой документ");
        return document;
    }

    public java.util.List<ru.kicksharing.documents.dto.DocumentMetadata> history(String auth, int limit, int offset) {
        UUID owner = actor(auth);
        if (limit < 1 || limit > 100 || offset < 0) throw ApiException.badRequest("Некорректные limit/offset");
        repository.expirePending(owner);
        return repository.findByUserId(owner, limit, offset).stream().map(ru.kicksharing.documents.dto.DocumentMetadata::of).toList();
    }

    public ru.kicksharing.documents.dto.DocumentMetadata metadata(String auth, UUID id) {
        return ru.kicksharing.documents.dto.DocumentMetadata.of(owned(id, actor(auth), false));
    }

    public void delete(String auth, UUID id) {
        UUID owner = actor(auth);
        Document document = owned(id, owner, true);
        if (!document.isDeleted()) repository.softDelete(id, owner);
    }

    public byte[] download(String auth, UUID id) {
        Document document = owned(id, actor(auth), false);
        if (!"GENERATED".equals(document.getStatus()))
            throw ApiException.conflict("Документ не был сформирован: повторите генерацию");
        GenerateDocumentRequest req = new GenerateDocumentRequest();
        req.setUserId(document.getUserId());
        req.setDocType(document.getDocumentType());
        req.setSourceId(document.getSourceId());
        req.setFrom(document.getPeriodFrom());
        req.setTo(document.getPeriodTo());
        Object data;
        if (document.getPayloadJson() != null) {
            Class<?> type = switch (document.getDocumentType()) {
                case "RIDE_PAYMENT" -> RidePaymentDto.class;
                case "USER_BLOCKED" -> UserBlockedDto.class;
                case "USER_HISTORY" -> UserHistoryDto.class;
                default -> throw new ApiException(502, "Неизвестный тип снимка документа");
            };
            try {
                data = ru.kicksharing.documents.util.JsonUtil.fromJson(document.getPayloadJson(), type);
                if (data == null) throw new IllegalArgumentException();
            } catch (RuntimeException e) {
                throw new ApiException(502, "Повреждён снимок документа");
            }
        } else {
            data = buildData(req, auth.substring(7).trim(), document.getDocumentType());
            repository.markGenerated(id, ru.kicksharing.documents.util.JsonUtil.toJson(data), document.getGeneratedAt());
        }
        return renderData(document.getDocumentType(), data);
    }

    private String normalizeDocType(String docType) {
        if ("RECEIPT".equals(docType)) return "RIDE_PAYMENT";
        return docType;
    }

    private RidePaymentDto buildRidePayment(GenerateDocumentRequest req, String token) {
        if (req.getSourceId() == null) throw ApiException.badRequest("sourceId обязателен для RIDE_PAYMENT");

        RideResponse ride = rideClient.getRide(req.getSourceId().toString(), token);
        if (!ru.kicksharing.documents.client.ServiceHttp.sameId(ride.getUserId(), req.getUserId().toString()))
            throw ApiException.forbidden("Чужая поездка");
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

        return dto;
    }

    private UserBlockedDto buildUserBlocked(GenerateDocumentRequest req, String token) {
        ModerationResponse status = moderationClient.getStatus(req.getUserId().toString(), token);
        if (!status.isBlocked()) throw ApiException.conflict("Пользователь не заблокирован");

        UserResponse user = userClient.getUser(req.getUserId().toString(), token);

        UserBlockedDto dto = new UserBlockedDto();
        dto.setUserFullName(user.getFullName());
        dto.setUserLogin(user.getLogin());
        dto.setReason(status.getReason());
        dto.setBlockedAt(status.getBlockedAt());
        dto.setBlockedUntil(status.getBlockedUntil());

        return dto;
    }

    private UserHistoryDto buildUserHistory(GenerateDocumentRequest req, String token) {
        if (req.getFrom() == null || req.getTo() == null) {
            throw ApiException.badRequest("from и to обязательны для USER_HISTORY");
        }
        UserResponse user = userClient.getUser(req.getUserId().toString(), token);
        List<RideResponse> rides = rideClient.getUserRides(req.getUserId().toString(), token);

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

        return dto;
    }
}