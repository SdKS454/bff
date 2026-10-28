package ru.itis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatusResponse {

    private UUID userId;
    private boolean blocked;
    private String reasonType;
    private String reason;
    private String blockedAt;
    private String blockedUntil;

    public static StatusResponse notBlocked(UUID userId) {
        return new StatusResponse(userId, false, null, null, null, null);
    }
}
