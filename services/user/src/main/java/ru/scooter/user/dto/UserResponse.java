package ru.scooter.user.dto;

import ru.scooter.user.entity.Role;
import ru.scooter.user.entity.User;

import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String login,
        Role role,
        String fullName,
        LocalDate birthDate,
        String avatarUrl,
        boolean deleted,
        boolean blocked) {
    public static UserResponse from(User user, boolean blocked) {
        return new UserResponse(
                user.id(),
                user.login(),
                user.role(),
                user.fullName(),
                user.birthDate(),
                user.avatarUrl(),
                user.deleted(),
                blocked);
    }
}
