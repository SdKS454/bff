package ru.scooter.user.service;

import com.fasterxml.jackson.databind.JsonNode;

import ru.scooter.user.client.ModerationClient;
import ru.scooter.user.dto.*;
import ru.scooter.user.util.Validation;

import java.util.UUID;

public final class ModerationService {
    private final UserService users;
    private final ModerationClient moderation;

    public ModerationService(UserService users, ModerationClient moderation) {
        this.users = users;
        this.moderation = moderation;
    }

    public JsonNode ban(UUID id, BanRequest request, AuthPrincipal principal) {
        UserService.requireAdmin(principal);
        users.existing(id);
        if (request.reason() == null || !request.reason().matches("[A-Z][A-Z0-9_]{0,63}"))
            throw Validation.bad("Укажите reasonType из справочника модерации в поле reason");
        return moderation.ban(id, request.reason(), principal.rawToken());
    }

    public JsonNode unban(UUID id, AuthPrincipal principal) {
        UserService.requireAdmin(principal);
        users.existing(id);
        return moderation.unban(id, principal.rawToken());
    }

    public JsonNode list(String limit, String offset, AuthPrincipal principal) {
        UserService.requireAdmin(principal);
        return moderation.blockedUsers(
                number(limit, 100, 1, 500),
                number(offset, 0, 0, Integer.MAX_VALUE),
                principal.rawToken());
    }

    public JsonNode reasons(AuthPrincipal principal) {
        UserService.requireAdmin(principal);
        return moderation.reasons(principal.rawToken());
    }

    private int number(String value, int fallback, int min, int max) {
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw Validation.bad("Некорректные limit/offset");
            return parsed;
        } catch (NumberFormatException e) {
            throw Validation.bad("Некорректные limit/offset");
        }
    }
}
