package bff.service.impl;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;




@WebListener
public final class HttpClients implements ServletContextListener {
    private static HttpClient client;
    private static ExecutorService executor;

    static synchronized HttpClient shared() {
        if (client == null) {
            AtomicInteger sequence = new AtomicInteger();
            executor = Executors.newCachedThreadPool(task -> {
                Thread worker = new Thread(task, "bff-http-" + sequence.incrementAndGet());
                worker.setDaemon(true);
                worker.setContextClassLoader(ClassLoader.getPlatformClassLoader());
                return worker;
            });

            Thread thread = Thread.currentThread();
            ClassLoader original = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
                client = HttpClient.newBuilder().executor(executor).connectTimeout(Duration.ofSeconds(3)).build();
            } finally {
                thread.setContextClassLoader(original);
            }
        }
        return client;
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        ExecutorService workers;
        synchronized (HttpClients.class) {
            client = null;
            workers = executor;
            executor = null;
        }
        if (workers != null) {
            workers.shutdownNow();
            try {
                if (!workers.awaitTermination(5, TimeUnit.SECONDS))
                    event.getServletContext().log("HTTP workers did not terminate within 5 seconds");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
