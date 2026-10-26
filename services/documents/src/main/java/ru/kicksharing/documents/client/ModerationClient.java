package ru.kicksharing.documents.client;

import ru.kicksharing.documents.dto.external.ModerationResponse;

public class ModerationClient {
    public ModerationResponse getStatus(String userId, String token) {
        var value = ServiceHttp.json("moderation.service.url", "/api/moderation/users/" + userId + "/status", token);
        if (!value.path("blocked").isBoolean()) throw ServiceHttp.badResponse();
        ModerationResponse status = ServiceHttp.object(value, ModerationResponse.class);
        if (!ServiceHttp.sameId(status.getUserId(), userId)) throw ServiceHttp.badResponse();
        if (status.isBlocked() && (status.getReasonType() == null || status.getReasonType().isBlank()
                || status.getReason() == null || status.getReason().isBlank() || status.getBlockedAt() == null
                || (status.getBlockedUntil() != null && !status.getBlockedUntil().isAfter(status.getBlockedAt()))))
            throw ServiceHttp.badResponse();
        return status;
    }
}
