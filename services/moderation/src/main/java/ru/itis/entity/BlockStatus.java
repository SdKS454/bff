package ru.itis.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockStatus {
    private UUID userId;
    private boolean blocked;
    private String reasonType;
    private String reason;
    private Instant blockedAt;
    private Instant blockedUntil;

    public static BlockStatus notBlocked(UUID userId) {

        return new BlockStatus(
                userId,
                false,
                null,
                null,
                null,
                null
        );
    }
}
