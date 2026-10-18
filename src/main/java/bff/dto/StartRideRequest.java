package bff.dto;

import java.util.UUID;

public record StartRideRequest(String numberPlate, UUID rateId, String posX, String posY) {
}
