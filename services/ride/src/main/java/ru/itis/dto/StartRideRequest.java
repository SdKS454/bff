package ru.itis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StartRideRequest {
    private String numberPlate;
    private UUID rateId;
    private Double posX;
    private Double posY;
}
