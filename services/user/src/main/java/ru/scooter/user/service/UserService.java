package ru.scooter.user.service;

import ru.scooter.user.client.ModerationClient;
import ru.scooter.user.dto.*;
import ru.scooter.user.entity.*;
import ru.scooter.user.exception.ApiException;
import ru.scooter.user.repository.UserRepository;
import ru.scooter.user.util.Validation;

import java.util.*;

public final class UserService {
    private final UserRepository users;
    private final ModerationClient moderation;

    public UserService(UserRepository users, ModerationClient moderation) {
        this.users = users;
        this.moderation = moderation;
    }

    public boolean exists(String login) {
        return users.existsByLogin(Validation.login(login));
    }

    public List<UserSummary> list(AuthPrincipal principal) {
        requireAdmin(principal);
        return users.findActiveUsers();
    }

    public UserResponse me(AuthPrincipal principal) {
        return UserResponse.from(
                principal.user(),
                moderation.isBlocked(principal.user().id(), principal.rawToken()));
    }

    public UserResponse byId(UUID id, AuthPrincipal principal) {
        requireSelfOrAdmin(id, principal);
        User user = existing(id);
        return UserResponse.from(user, moderation.isBlocked(id, principal.rawToken()));
    }

    public boolean blocked(UUID id, AuthPrincipal principal) {
        requireSelfOrAdmin(id, principal);
        existing(id);
        return moderation.isBlocked(id, principal.rawToken());
    }

    public User existing(UUID id) {
        return users.findById(id)
                .filter(u -> !u.deleted())
                .orElseThrow(() -> new ApiException(404, "Пользователь не найден"));
    }

    public static void requireAdmin(AuthPrincipal principal) {
        if (principal.user().role() != Role.ADMIN)
            throw new ApiException(403, "Требуется роль ADMIN");
    }

    public static void requireSelfOrAdmin(UUID id, AuthPrincipal principal) {
        if (principal.user().role() != Role.ADMIN && !principal.user().id().equals(id))
            throw new ApiException(403, "Нет доступа к чужому пользователю");
    }
}
