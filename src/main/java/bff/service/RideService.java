package bff.service;

import bff.dto.EndRideRequest;
import bff.dto.JsonResponse;
import bff.dto.StartRideRequest;

import java.util.UUID;

public interface RideService {

    JsonResponse start(String jwt, StartRideRequest request);

    JsonResponse position(String jwt, UUID rideId, EndRideRequest request);

    JsonResponse finish(String jwt, UUID rideId, EndRideRequest request);

    JsonResponse pay(String jwt, UUID rideId);

    JsonResponse review(String jwt, UUID rideId, String json);

    JsonResponse findByUser(String jwt, UUID userId);
}
