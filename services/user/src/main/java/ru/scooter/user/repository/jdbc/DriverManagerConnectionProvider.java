package ru.scooter.user.repository.jdbc;

import ru.scooter.user.repository.ConnectionProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DriverManagerConnectionProvider implements ConnectionProvider {
    private final String url;
    private final Properties properties = new Properties();

    public DriverManagerConnectionProvider(String url, String user, String password) {
        this.url = url;
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "3");
        properties.setProperty("socketTimeout", "5");
        properties.setProperty("ApplicationName", "scooter-user-service");
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PostgreSQL driver is missing", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, properties);
    }
}
