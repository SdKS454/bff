package bff.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class ServiceUrls {

    private static final Properties PROPERTIES = new Properties();

    static {
        try (InputStream in = ServiceUrls.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                PROPERTIES.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ServiceUrls() {
    }

    static String get(String key, String fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = PROPERTIES.getProperty(key, fallback);
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
