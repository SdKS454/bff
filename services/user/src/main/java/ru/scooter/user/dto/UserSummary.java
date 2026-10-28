package ru.scooter.user.dto;

import java.util.UUID;

public record UserSummary(UUID id, String login, String fullName) {
}
