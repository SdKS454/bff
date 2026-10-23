package ru.itis.dao;

import ru.itis.entity.BlockedUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockDao {

    Optional<BlockedUser> findByUserId(UUID userId);

    boolean insertBlock(BlockedUser blockedUser);

    boolean deleteByUserId(UUID userId);

    List<BlockedUser> findAll(int limit, int offset);
}
