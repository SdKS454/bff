package ru.scooter.user.repository.jdbc;

import ru.scooter.user.entity.Token;
import ru.scooter.user.exception.DataAccessException;
import ru.scooter.user.repository.ConnectionProvider;
import ru.scooter.user.repository.TokenRepository;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public final class JdbcTokenRepository implements TokenRepository {
    private final ConnectionProvider connections;

    public JdbcTokenRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void insert(Token token) {
        try (Connection c = connections.getConnection();
             PreparedStatement s =
                     c.prepareStatement(
                             "INSERT INTO"
                                     + " tokens(id,user_id,token_hash,created_at,expire_at,is_valid)"
                                     + " VALUES (?,?,?,?,?,?)")) {
            s.setObject(1, token.id());
            s.setObject(2, token.userId());
            s.setString(3, token.tokenHash());
            s.setTimestamp(4, Timestamp.from(token.createdAt()));
            s.setTimestamp(5, Timestamp.from(token.expiresAt()));
            s.setBoolean(6, token.valid());
            s.executeUpdate();
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    @Override
    public Optional<Token> findById(UUID id) {
        try (Connection c = connections.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT * FROM tokens WHERE id=?")) {
            s.setObject(1, id);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(
                        new Token(
                                rs.getObject("id", UUID.class),
                                rs.getObject("user_id", UUID.class),
                                rs.getString("token_hash"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("expire_at").toInstant(),
                                rs.getBoolean("is_valid")));
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    @Override
    public void revoke(UUID id) {
        try (Connection c = connections.getConnection();
             PreparedStatement s =
                     c.prepareStatement("UPDATE tokens SET is_valid=FALSE WHERE id=?")) {
            s.setObject(1, id);
            s.executeUpdate();
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    private DataAccessException failure(SQLException e) {
        return new DataAccessException("Token storage is unavailable", e);
    }
}
