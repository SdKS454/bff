package ru.kicksharing.documents.client;

import ru.kicksharing.documents.dto.external.RideResponse;
import java.util.*;

public class RideClient {
    public RideResponse getRide(String rideId, String token) {
        RideResponse ride = ServiceHttp.object(ServiceHttp.json("ride.service.url", "/api/rides/" + rideId, token), RideResponse.class);
        if (!ServiceHttp.sameId(ride.getId(), rideId)) throw ServiceHttp.badResponse();
        validate(ride);
        return ride;
    }

    public List<RideResponse> getUserRides(String userId, String token) {
        var value = ServiceHttp.json("ride.service.url", "/api/rides?userId=" + userId, token);
        if (!value.isArray()) throw ServiceHttp.badResponse();
        List<RideResponse> rides = new ArrayList<>();
        for (var item : value) {
            RideResponse ride = ServiceHttp.object(item, RideResponse.class);
            validate(ride);
            // Do not silently produce an incomplete history from another user's data.
            if (!ServiceHttp.sameId(ride.getUserId(), userId)) throw ServiceHttp.badResponse();
            rides.add(ride);
        }
        return rides;
    }

    private static void validate(RideResponse ride) {
        if (!ServiceHttp.sameId(ride.getId(), ride.getId()) || !ServiceHttp.sameId(ride.getUserId(), ride.getUserId())
                || ride.getNumberPlate() == null || ride.getNumberPlate().isBlank()
                || ride.getStatus() == null || !Set.of("ACTIVE", "FINISHED", "PAID", "CANCELLED").contains(ride.getStatus())
                || ride.getStartedAt() == null || (ride.getAmount() != null && ride.getAmount().signum() < 0))
            throw ServiceHttp.badResponse();
        if ("PAID".equals(ride.getStatus()) && (ride.getAmount() == null || ride.getPaidAt() == null
                || ride.getFinishedAt() == null || ride.getCurrency() == null || !ride.getCurrency().matches("[A-Z]{3}")
                || ride.getFinishedAt().isBefore(ride.getStartedAt()) || ride.getPaidAt().isBefore(ride.getFinishedAt())))
            throw ServiceHttp.badResponse();
    }
}
