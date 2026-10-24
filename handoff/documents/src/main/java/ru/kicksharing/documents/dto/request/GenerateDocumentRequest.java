package ru.kicksharing.documents.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public class GenerateDocumentRequest {
    private UUID userId;
    private String docType;       // RIDE_PAYMENT / RECEIPT / USER_BLOCKED / USER_HISTORY
    private UUID sourceId;        // для RIDE_PAYMENT
    private LocalDate from;       // для USER_HISTORY
    private LocalDate to;         // для USER_HISTORY

    // Геттеры и сеттеры
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }

    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
}