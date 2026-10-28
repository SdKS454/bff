package ru.scooter.user.dto;

public record RegisterRequest(
        String login,
        String password,
        String fullName,
        String birthDate,
        String avatarUrl,
        String role) {
}
