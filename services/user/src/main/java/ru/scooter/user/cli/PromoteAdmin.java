package ru.scooter.user.cli;

import ru.scooter.user.repository.jdbc.DriverManagerConnectionProvider;
import ru.scooter.user.util.Validation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public final class PromoteAdmin {
    private PromoteAdmin() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("Usage: PromoteAdmin <existing-login>");
        String login = Validation.login(args[0]);
        DriverManagerConnectionProvider connections =
                new DriverManagerConnectionProvider(
                        env("DB_URL"), env("DB_USER"), env("DB_PASSWORD"));
        try (Connection c = connections.getConnection()) {
            c.setAutoCommit(false);
            try {
                UUID id;
                try (PreparedStatement select =
                             c.prepareStatement(
                                     "SELECT id FROM users WHERE login=? AND NOT is_deleted FOR"
                                             + " UPDATE")) {
                    select.setString(1, login);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next())
                            throw new IllegalArgumentException("Register this login first");
                        id = rs.getObject(1, UUID.class);
                    }
                }
                try (PreparedStatement update =
                             c.prepareStatement("UPDATE users SET role='ADMIN' WHERE id=?")) {
                    update.setObject(1, id);
                    update.executeUpdate();
                }
                try (PreparedStatement revoke =
                             c.prepareStatement("UPDATE tokens SET is_valid=FALSE WHERE user_id=?")) {
                    revoke.setObject(1, id);
                    revoke.executeUpdate();
                }
                c.commit();
                System.out.println("ADMIN granted to " + login + "; sign in again");
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing environment variable: " + name);
        return value;
    }
}
