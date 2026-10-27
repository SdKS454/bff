package ru.kicksharing.documents.dto;
import ru.kicksharing.documents.entity.Document;
import java.util.UUID;
import java.time.*;
public record DocumentMetadata(UUID id, UUID userId, String docType, UUID sourceId,
 LocalDate from, LocalDate to, String status, OffsetDateTime createdAt, OffsetDateTime generatedAt, boolean snapshotAvailable) {
 public static DocumentMetadata of(Document d){return new DocumentMetadata(d.getId(),d.getUserId(),d.getDocumentType(),d.getSourceId(),d.getPeriodFrom(),d.getPeriodTo(),d.getStatus(),d.getCreatedAt(),d.getGeneratedAt(),d.getPayloadJson()!=null);}
}
