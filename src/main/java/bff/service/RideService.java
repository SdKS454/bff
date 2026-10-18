package bff.service;

import java.util.UUID;

import bff.dto.EndRideRequest;
import bff.dto.JsonResponse;
import bff.dto.StartRideRequest;

public interface RideService {

    JsonResponse start(String jwt, StartRideRequest request);

    JsonResponse finish(String jwt, UUID rideId, EndRideRequest request);

    JsonResponse pay(String jwt, UUID rideId);

    JsonResponse findByUser(String jwt, UUID userId);
}
