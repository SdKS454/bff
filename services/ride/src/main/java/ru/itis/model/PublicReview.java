package ru.itis.model;

import java.time.Instant;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Deliberately excludes user identity, ride IDs, plates, payments and coordinates. */
public record PublicReview(int rideRating, int scooterRating, String rideComment, String scooterComment,
        @JsonSerialize(using = ToStringSerializer.class) Instant reviewedAt) { }
