package ru.itis.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {
    public Connection getConnection() throws SQLException {
        try { Class.forName("org.postgresql.Driver"); } catch(ClassNotFoundException e) { throw new IllegalStateException(e); }
        return DriverManager.getConnection(System.getenv("DB_URL"),
                System.getenv().getOrDefault("DB_USER", System.getenv("DB_USERNAME")), System.getenv("DB_PASSWORD"));
    }
}
