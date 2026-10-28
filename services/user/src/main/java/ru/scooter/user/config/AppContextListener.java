package ru.scooter.user.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import ru.scooter.user.client.HttpModerationClient;
import ru.scooter.user.client.ModerationClient;
import ru.scooter.user.repository.ConnectionProvider;
import ru.scooter.user.repository.TokenRepository;
import ru.scooter.user.repository.UserRepository;
import ru.scooter.user.repository.jdbc.DriverManagerConnectionProvider;
import ru.scooter.user.repository.jdbc.JdbcTokenRepository;
import ru.scooter.user.repository.jdbc.JdbcUserRepository;
import ru.scooter.user.service.AuthService;
import ru.scooter.user.service.ModerationService;
import ru.scooter.user.service.UserService;
import ru.scooter.user.util.BCryptPasswordHasher;
import ru.scooter.user.util.JwtUtil;
import ru.scooter.user.util.PemKeys;

import java.time.Clock;
import java.time.Duration;

public final class AppContextListener implements ServletContextListener {
    public static final String COMPONENTS = AppComponents.class.getName();

    public static AppComponents components(ServletContext context) {
        Object value = context.getAttribute(COMPONENTS);
        if (!(value instanceof AppComponents components))
            throw new IllegalStateException("Application is not initialized");
        return components;
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            AppConfig config = AppConfig.fromEnvironment();
            Clock clock = Clock.systemUTC();
            ConnectionProvider connections =
                    new DriverManagerConnectionProvider(
                            config.dbUrl(), config.dbUser(), config.dbPassword());
            try (java.sql.Connection c = connections.getConnection();
                 java.sql.Statement s = c.createStatement()) {
                s.executeQuery(
                                "SELECT"
                                        + " id,login,password_hash,full_name,birth_date,avatar_url,role,created_at,updated_at,is_deleted,deleted_at"
                                        + " FROM users WHERE FALSE")
                        .close();
                s.executeQuery(
                                "SELECT id,user_id,token_hash,created_at,expire_at,is_valid FROM"
                                        + " tokens WHERE FALSE")
                        .close();
            }
            UserRepository users = new JdbcUserRepository(connections);
            TokenRepository tokens = new JdbcTokenRepository(connections);
            JwtUtil jwt =
                    new JwtUtil(
                            PemKeys.privateKey(config.privateKeyPath()),
                            PemKeys.publicKey(config.publicKeyPath()),
                            config.jwtIssuer(),
                            config.jwtTtlSeconds(),
                            clock);
            ModerationClient client =
                    new HttpModerationClient(
                            config.moderationBaseUrl(),
                            Duration.ofSeconds(config.moderationTimeoutSeconds()));
            UserService userService = new UserService(users, client);
            AuthService auth =
                    new AuthService(
                            users,
                            tokens,
                            new BCryptPasswordHasher(config.bcryptCost()),
                            jwt,
                            client,
                            clock);
            event.getServletContext()
                    .setAttribute(
                            COMPONENTS,
                            new AppComponents(
                                    config,
                                    auth,
                                    userService,
                                    new ModerationService(userService, client)));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "User Service startup failed: check environment, schema and RSA key pair", e);
        }
    }
}
