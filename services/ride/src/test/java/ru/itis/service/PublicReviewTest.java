package ru.itis.service;

import org.junit.jupiter.api.Test;
import ru.itis.config.DatabaseConfig;
import ru.itis.exception.ApiException;
import ru.itis.model.PublicReview;
import ru.itis.repository.impl.PublicReviewRepository;
import ru.itis.util.Json;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PublicReviewTest {
    @Test
    void publicPayloadContainsOnlyRatingsCommentsAndTimestamp() {
        var tree = Json.MAPPER.valueToTree(new PublicReview(5, 4, "Хорошо", "Самокат", Instant.now()));
        Set<String> fields = new HashSet<>();
        tree.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("rideRating", "scooterRating", "rideComment", "scooterComment", "reviewedAt"), fields);
    }

    @Test
    void rejectsUnboundedPagesBeforeOpeningDatabaseConnection() {
        var database = mock(DatabaseConfig.class);
        var repository = new PublicReviewRepository(database);
        for (int[] page : List.of(new int[]{0, 0}, new int[]{101, 0}, new int[]{20, -1}))
            assertEquals(400, assertThrows(ApiException.class, () -> repository.list(page[0], page[1])).getStatus());
        verifyNoInteractions(database);
    }
}
