package ru.kicksharing.documents.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Document {
    private UUID id;
    private UUID userId;
    private String documentType; // RIDE_PAYMENT, USER_BLOCKED, USER_HISTORY
    private UUID sourceId;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private String status; // PENDING, GENERATED, FAILED
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime generatedAt;
    private boolean isDeleted;
    private OffsetDateTime deletedAt;

    // Пустой конструктор (нужен для маппинга из ResultSet)
    public Document() {}

    // Конструктор для создания нового документа перед сохранением
    public Document(UUID userId, String documentType, UUID sourceId, LocalDate periodFrom, LocalDate periodTo) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.documentType = documentType;
        this.sourceId = sourceId;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.status = "PENDING";
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        this.isDeleted = false;
    }

    // --- Геттеры и сеттеры ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }

    public LocalDate getPeriodFrom() { return periodFrom; }
    public void setPeriodFrom(LocalDate periodFrom) { this.periodFrom = periodFrom; }

    public LocalDate getPeriodTo() { return periodTo; }
    public void setPeriodTo(LocalDate periodTo) { this.periodTo = periodTo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
