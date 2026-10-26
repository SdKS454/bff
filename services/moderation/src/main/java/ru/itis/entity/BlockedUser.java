package ru.itis.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itis.entity.enums.BlockReason;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockedUser {
    private UUID userId;
    private BlockReason reasonType;
    private Instant blockedAt;
    private Instant blockedUntil;

}
