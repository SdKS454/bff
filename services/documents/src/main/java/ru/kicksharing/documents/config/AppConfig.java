package ru.kicksharing.documents.config;

import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final Properties props = new Properties();

    static {
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new RuntimeException("Не найден application.properties");
            }
            props.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки конфигурации", e);
        }
    }

    // Метод, который сначала смотрит в переменные окружения (для Docker), потом в properties
    public static String get(String key) {
        String property = System.getProperty("documents." + key);
        if (property != null) return property;
        String envKey = key.replace('.', '_').toUpperCase();
        String envValue = System.getenv(envKey);
        if(envValue==null && key.equals("db.username"))envValue=System.getenv("DB_USER");
        return envValue != null ? envValue : props.getProperty(key);
    }
}
