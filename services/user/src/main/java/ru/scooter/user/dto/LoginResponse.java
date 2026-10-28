package ru.scooter.user.dto;

public record LoginResponse(String accessToken, long expiresInSeconds, UserResponse user) {
}
