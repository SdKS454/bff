package bff.dto;

import java.util.UUID;

public record UserDto(
        UUID id,
        String login,
        String role,
        String fullName,
        String birthDate,
        String avatarUrl,
        boolean deleted,
        boolean blocked) {
}
