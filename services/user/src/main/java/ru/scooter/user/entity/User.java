package ru.scooter.user.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record User(
        UUID id,
        String login,
        String passwordHash,
        String fullName,
        LocalDate birthDate,
        String avatarUrl,
        Role role,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted,
        Instant deletedAt) {}
