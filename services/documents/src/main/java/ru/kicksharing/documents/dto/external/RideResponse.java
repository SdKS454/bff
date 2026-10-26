package ru.kicksharing.documents.dto.external;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class RideResponse {
    private String id;
    private String userId;
    private String numberPlate;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private BigDecimal amount;
    private String currency;
    private String paymentMode;
    public String getPaymentMode(){return paymentMode;}
    public void setPaymentMode(String value){paymentMode=value;}
    private OffsetDateTime paidAt;

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getNumberPlate() { return numberPlate; }
    public void setNumberPlate(String numberPlate) { this.numberPlate = numberPlate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }
}