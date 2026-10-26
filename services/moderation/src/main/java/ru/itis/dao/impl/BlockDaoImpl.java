package ru.itis.dao.impl;

import ru.itis.dao.BlockDao;
import ru.itis.entity.BlockedUser;
import ru.itis.entity.enums.BlockReason;
import ru.itis.util.DbConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BlockDaoImpl implements BlockDao {

    private static final String SQL_FIND_BY_USERID = "SELECT b.user_id, r.type AS reason_type, b.blocked_at, b.blocked_until FROM blocked_users b JOIN reasons r ON r.id = b.reason_id WHERE b.user_id=?;";
    private static final String SQL_INSERT_BLOCK = "INSERT INTO blocked_users (user_id, reason_id, blocked_at, blocked_until) VALUES (?, (SELECT id FROM reasons WHERE type = ?), ?, ?) ON CONFLICT (user_id) DO NOTHING;";
    private static final String SQL_DELETE_BY_USERID = "DELETE FROM blocked_users WHERE user_id=?;";
    private static final String SQL_FIND_ALL = "SELECT b.user_id, r.type AS reason_type, b.blocked_at, b.blocked_until FROM blocked_users b JOIN reasons r ON r.id = b.reason_id WHERE b.blocked_until IS NULL OR b.blocked_until > NOW() ORDER BY b.blocked_at DESC LIMIT ? OFFSET ?;";

    @Override
    public Optional<BlockedUser> findByUserId(UUID userId) {
        try (Connection connection = DbConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SQL_FIND_BY_USERID)) {
            preparedStatement.setObject(1, userId);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return Optional.of(mapper(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения пользователя", e);
        }
    }

    @Override
    public boolean insertBlock(BlockedUser blockedUser) {
        try (Connection connection = DbConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT_BLOCK)) {
            preparedStatement.setObject(1, blockedUser.getUserId());
            preparedStatement.setObject(2, blockedUser.getReasonType().name());
            preparedStatement.setTimestamp(3, Timestamp.from(blockedUser.getBlockedAt()));
            preparedStatement.setTimestamp(4, blockedUser.getBlockedUntil() == null ? null : Timestamp.from(blockedUser.getBlockedUntil()));
            int rows = preparedStatement.executeUpdate();
            return rows == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка добавления блокировки пользователя", e);
        }
    }

    @Override
    public boolean deleteByUserId(UUID userId) {
        try (Connection connection = DbConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SQL_DELETE_BY_USERID)) {
            preparedStatement.setObject(1, userId);
            int rows = preparedStatement.executeUpdate();
            return rows == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка удаления блокировки пользователя", e);
        }
    }

    @Override
    public List<BlockedUser> findAll(int limit, int offset) {
        List<BlockedUser> blockedUsers = new ArrayList<>();
        try (Connection connection = DbConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SQL_FIND_ALL)) {

            preparedStatement.setInt(1, limit);
            preparedStatement.setInt(2, offset);
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()){
                blockedUsers.add(mapper(rs));
            }
            return blockedUsers;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения списка блокировок", e);
        }
    }

    private static BlockedUser mapper(ResultSet rs) throws SQLException {
        Timestamp blockedUntil = rs.getTimestamp("blocked_until");
        return new BlockedUser(
                UUID.fromString(rs.getString("user_id")),
                BlockReason.valueOf(rs.getString("reason_type")),
                rs.getTimestamp("blocked_at").toInstant(),
                blockedUntil == null ? null : blockedUntil.toInstant());
    }

}
