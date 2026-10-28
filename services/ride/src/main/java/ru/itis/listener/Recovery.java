package ru.itis.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import ru.itis.config.DatabaseConfig;
import ru.itis.repository.impl.RideRepositoryJdbcImpl;
import ru.itis.service.RideIntegrationService;
import ru.itis.service.impl.RideServiceImpl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@WebListener
public class Recovery implements ServletContextListener {
    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        var service = new RideServiceImpl(new RideRepositoryJdbcImpl(new DatabaseConfig()), new RideIntegrationService());
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ride-recovery");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                service.recover();
            } catch (RuntimeException e) {
                event.getServletContext().log("Ride recovery will retry", e);
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
