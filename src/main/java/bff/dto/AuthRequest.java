package bff.dto;

public record AuthRequest(
        String login,
        String password,
        String fullName,
        String birthDate,
        String avatarUrl) {
}
