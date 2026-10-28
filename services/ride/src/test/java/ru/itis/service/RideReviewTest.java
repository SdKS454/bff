package ru.itis.service;

import org.junit.jupiter.api.Test;
import ru.itis.dto.ReviewRequest;
import ru.itis.exception.ApiException;
import ru.itis.model.RideEntity;
import ru.itis.repository.RideRepository;
import ru.itis.service.impl.RideServiceImpl;
import ru.itis.util.Json;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RideReviewTest {
    @Test
    void rejectsForeignAndUnfinishedRidesBeforeWritingAnything() {
        var repo = mock(RideRepository.class);
        var integration = mock(RideIntegrationService.class);
        var ride = new RideEntity();
        ride.setId(UUID.randomUUID());
        ride.setUserId(UUID.randomUUID());
        ride.setStatus("ACTIVE");
        when(repo.findById(ride.getId())).thenReturn(Optional.of(ride));
        var service = new RideServiceImpl(repo, integration);
        var body = new ReviewRequest(5, 4, "", "");
        assertEquals(403, assertThrows(ApiException.class, () -> service.review(UUID.randomUUID(), ride.getId(), body)).getStatus());
        for (String status : List.of("ACTIVE", "STARTING", "CANCELLING", "FINISHING")) {
            ride.setStatus(status);
            assertEquals(409, assertThrows(ApiException.class, () -> service.review(ride.getUserId(), ride.getId(), body)).getStatus());
        }
        verify(repo, never()).review(any(), any());
        verifyNoInteractions(integration);
    }

    @Test
    void savesOwnedFinishedOrPaidRideWithoutTouchingPaymentOrScooter() {
        for (String status : List.of("FINISHED", "PAID")) {
            var repo = mock(RideRepository.class);
            var integration = mock(RideIntegrationService.class);
            var ride = new RideEntity();
            ride.setId(UUID.randomUUID());
            ride.setUserId(UUID.randomUUID());
            ride.setStatus(status);
            when(repo.findById(ride.getId())).thenReturn(Optional.of(ride));
            when(repo.review(any(), any())).thenReturn(true);
            new RideServiceImpl(repo, integration).review(ride.getUserId(), ride.getId(), new ReviewRequest(5, 4, " Хорошо ", "<script>text</script>"));
            verify(repo).review(eq(ride.getId()), argThat(r -> r.rideRating() == 5 && r.scooterRating() == 4 && r.rideComment().equals("Хорошо") && r.reviewedAt() != null));
            verifyNoInteractions(integration);
        }
    }

    @Test
    void rejectsInvalidRatingsCommentsAndScalarCoercion() throws Exception {
        for (String json : List.of("{\"rideRating\":0,\"scooterRating\":5}", "{\"rideRating\":5,\"scooterRating\":6}",
                "{\"rideRating\":1.5,\"scooterRating\":5}", "{\"rideRating\":\"5\",\"scooterRating\":5}",
                "{\"scooterRating\":5}", "{\"rideRating\":5,\"scooterRating\":5,\"rideComment\":3}")) {
            var body = Json.MAPPER.readTree(json);
            assertEquals(400, assertThrows(ApiException.class, () -> ReviewRequest.from(body)).getStatus());
        }
        assertThrows(ApiException.class, () -> new ReviewRequest(5, 5, "a".repeat(1001), ""));
        assertThrows(ApiException.class, () -> new ReviewRequest(5, 5, "\u0000", ""));
        assertEquals(1000, new ReviewRequest(5, 5, "я".repeat(1000), "").rideComment().length());
    }
}
