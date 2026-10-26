package ru.scooter.user.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public interface ModerationClient {
    boolean isBlocked(UUID userId, String token);

    JsonNode ban(UUID userId, String reason, String token);

    JsonNode unban(UUID userId, String token);

    JsonNode blockedUsers(int limit, int offset, String token);

    JsonNode reasons(String token);
}
