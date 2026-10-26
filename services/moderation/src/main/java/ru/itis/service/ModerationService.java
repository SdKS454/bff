package ru.itis.service;

import ru.itis.entity.BlockStatus;
import ru.itis.entity.Reason;

import java.util.List;
import java.util.UUID;

public interface ModerationService {

    BlockStatus getStatus(UUID userId);

    BlockStatus ban(UUID userId, String reason);

    BlockStatus unban(UUID userId);

    List<BlockStatus> listBlocked(int limit, int offset);

    List<Reason> getReasons();
}
