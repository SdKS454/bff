package ru.itis.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
public class Rate {
    private UUID id;
    private BigDecimal unlockFee;
    private BigDecimal pricePerMinute;
    private String currency;
}
