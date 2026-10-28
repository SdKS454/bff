package ru.kicksharing.documents.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = AppConfig.get("db.url");
    private static final String USER = AppConfig.get("db.username");
    private static final String PASSWORD = AppConfig.get("db.password");

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PostgreSQL driver not found", e);
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("08"))
                throw ru.kicksharing.documents.exception.ApiException.serviceUnavailable("База документов недоступна");
            throw e;
        }
    }
}