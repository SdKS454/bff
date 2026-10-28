package ru.itis.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class RideEntity {
    private RideReview review;
    private String paymentMode;
    private UUID id;
    private UUID userId;
    private String numberPlate;
    private UUID rateId;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class)
    private Instant startedAt;
    @JsonSerialize(using = ToStringSerializer.class)
    private Instant finishedAt;
    private Double startPosX;
    private Double startPosY;
    private Double endPosX;
    private Double endPosY;
    private Double lastPosX;
    private Double lastPosY;
    @JsonSerialize(using = ToStringSerializer.class)
    private Instant lastPositionAt;
    private Long durationSeconds;
    private BigDecimal amount;
    private String currency;
    @JsonSerialize(using = ToStringSerializer.class)
    private Instant paidAt;
    @JsonIgnore
    private BigDecimal unlockFee;
    @JsonIgnore
    private BigDecimal pricePerMinute;
}
