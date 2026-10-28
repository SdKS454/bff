package ru.itis.model;

import java.time.Instant;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record RideReview(int rideRating, int scooterRating, String rideComment, String scooterComment,
        @JsonSerialize(using = ToStringSerializer.class) Instant reviewedAt) { }
