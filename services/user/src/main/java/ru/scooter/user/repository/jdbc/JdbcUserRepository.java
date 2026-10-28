package ru.scooter.user.repository.jdbc;

import ru.scooter.user.dto.UserSummary;
import ru.scooter.user.entity.Role;
import ru.scooter.user.entity.User;
import ru.scooter.user.exception.DataAccessException;
import ru.scooter.user.exception.DuplicateLoginException;
import ru.scooter.user.repository.ConnectionProvider;
import ru.scooter.user.repository.UserRepository;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcUserRepository implements UserRepository {
    private final ConnectionProvider connections;

    public JdbcUserRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(User user) {
        String sql =
                "INSERT INTO users(id,login,password_hash,full_name,birth_date,avatar_url,role)"
                        + " VALUES (?,?,?,?,?,?,?)";
        try (Connection c = connections.getConnection();
             PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, user.id());
            s.setString(2, user.login());
            s.setString(3, user.passwordHash());
            s.setString(4, user.fullName());
            s.setObject(5, user.birthDate());
            s.setString(6, user.avatarUrl());
            s.setString(7, user.role().name());
            s.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new DuplicateLoginException(e);
            throw failure(e);
        }
    }

    @Override
    public Optional<User> findById(UUID id) {
        return find("id", id);
    }

    @Override
    public Optional<User> findByLogin(String login) {
        return find("login", login);
    }

    private Optional<User> find(String column, Object value) {
        try (Connection c = connections.getConnection();
             PreparedStatement s =
                     c.prepareStatement("SELECT * FROM users WHERE " + column + "=?")) {
            s.setObject(1, value);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    @Override
    public boolean existsByLogin(String login) {
        try (Connection c = connections.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT 1 FROM users WHERE login=?")) {
            s.setString(1, login);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    @Override
    public List<UserSummary> findActiveUsers() {
        try (Connection c = connections.getConnection();
             PreparedStatement s =
                     c.prepareStatement(
                             "SELECT id,login,full_name FROM users WHERE NOT is_deleted ORDER BY"
                                     + " login");
             ResultSet rs = s.executeQuery()) {
            List<UserSummary> users = new ArrayList<>();
            while (rs.next())
                users.add(
                        new UserSummary(
                                rs.getObject("id", UUID.class),
                                rs.getString("login"),
                                rs.getString("full_name")));
            return users;
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        Timestamp deleted = rs.getTimestamp("deleted_at");
        return new User(
                rs.getObject("id", UUID.class),
                rs.getString("login"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                rs.getObject("birth_date", LocalDate.class),
                rs.getString("avatar_url"),
                Role.valueOf(rs.getString("role")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getBoolean("is_deleted"),
                deleted == null ? null : deleted.toInstant());
    }

    private DataAccessException failure(SQLException e) {
        return new DataAccessException("User storage is unavailable", e);
    }
}
