package ru.itis.service.impl;

import ru.itis.dao.BlockDao;
import ru.itis.dao.ReasonDao;
import ru.itis.entity.BlockStatus;
import ru.itis.entity.BlockedUser;
import ru.itis.entity.Reason;
import ru.itis.entity.enums.BlockReason;
import ru.itis.exception.ValidationException;
import ru.itis.service.ModerationService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ModerationServiceImpl implements ModerationService {

    private static final int MAX_LIMIT = 500;

    private final BlockDao blockDao;
    private final ReasonDao reasonDao;

    public ModerationServiceImpl(BlockDao blockDao, ReasonDao reasonDao) {
        this.blockDao = blockDao;
        this.reasonDao = reasonDao;
    }

    @Override
    public BlockStatus getStatus(UUID userId) {
        Optional<BlockedUser> found = blockDao.findByUserId(userId);
        if (found.isEmpty()) {
            return BlockStatus.notBlocked(userId);
        }
        Instant until = found.get().getBlockedUntil();
        if (until != null && until.isBefore(Instant.now())) {
            blockDao.deleteByUserId(userId);
            return BlockStatus.notBlocked(userId);
        }
        return toStatus(found.get());
    }

    @Override
    public BlockStatus ban(UUID userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Не указана причина блокировки");
        }
        BlockReason type;
        try {
            type = BlockReason.valueOf(reason);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Неизвестная причина блокировки");
        }

        BlockStatus current = getStatus(userId);
        if (current.isBlocked()) {
            return current;
        }

        Instant now = Instant.now();
        Instant until = type.getDefaultDurationSeconds().map(now::plusSeconds).orElse(null);
        blockDao.insertBlock(new BlockedUser(userId, type, now, until));
        return getStatus(userId);
    }

    @Override
    public BlockStatus unban(UUID userId) {
        blockDao.deleteByUserId(userId);
        return BlockStatus.notBlocked(userId);
    }

    @Override
    public List<BlockStatus> listBlocked(int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT || offset < 0) {
            throw new ValidationException("Некорректные limit или offset");
        }
        List<BlockStatus> result = new ArrayList<>();
        for (BlockedUser blockedUser : blockDao.findAll(limit, offset)) {
            result.add(toStatus(blockedUser));
        }
        return result;
    }

    @Override
    public List<Reason> getReasons() {
        List<Reason> reasons = new ArrayList<>(reasonDao.findAll());
        reasons.sort(Comparator.comparingInt(reason -> reason.getReasonType().ordinal()));
        return reasons;
    }

    private BlockStatus toStatus(BlockedUser blockedUser) {
        return new BlockStatus(
                blockedUser.getUserId(),
                true,
                blockedUser.getReasonType().name(),
                blockedUser.getReasonType().getDescription(),
                blockedUser.getBlockedAt(),
                blockedUser.getBlockedUntil());
    }
}
