package ru.itis.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.Instant;

public record RideReview(int rideRating, int scooterRating, String rideComment, String scooterComment,
                         @JsonSerialize(using = ToStringSerializer.class) Instant reviewedAt) {
}
