package bff.dto;

public record AuthResponse(String accessToken, long expiresInSeconds, UserDto user) {
}
