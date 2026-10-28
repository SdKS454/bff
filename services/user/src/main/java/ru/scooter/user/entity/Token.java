package ru.scooter.user.entity;

import java.time.Instant;
import java.util.UUID;

public record Token(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant createdAt,
        Instant expiresAt,
        boolean valid) {
}
