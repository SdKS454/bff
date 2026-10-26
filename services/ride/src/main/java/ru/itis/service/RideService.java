package ru.itis.service;

import ru.itis.model.RideEntity;

import java.util.List;
import java.util.UUID;

public interface RideService {
    RideEntity start(UUID userId, String numberPlate, UUID rateId, Double posX, Double posY);

    RideEntity finish(UUID userId, UUID id, double posX, double posY);

    RideEntity get(UUID userId, UUID id);

    RideEntity pay(UUID userId, UUID id);

    List<RideEntity> history(UUID userId, UUID requestedUserId);
}
