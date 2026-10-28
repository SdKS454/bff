package ru.kicksharing.documents.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class JwtPublicKeyLoader {
    private JwtPublicKeyLoader() {
    }

    public static PublicKey load(String inline, String configuredPath) {
        String pem = inline;
        if (configuredPath != null && !configuredPath.isBlank()) {
            String path = configuredPath.trim();
            if (path.startsWith("~/")) path = System.getProperty("user.home") + path.substring(1);
            try {
                pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException("Documents не может прочитать JWT_PUBLIC_KEY_PATH: проверьте путь и права процесса Tomcat", e);
            }
        }
        if (pem == null || pem.isBlank())
            throw new IllegalStateException("В процессе Documents не задан JWT_PUBLIC_KEY_PATH (путь к public.pem сервиса User)");
        try {
            String base64 = pem.replace("\ufeff", "").replace("\\r", "").replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s+", "");
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
            if (((java.security.interfaces.RSAPublicKey) key).getModulus().bitLength() < 2048)
                throw new IllegalArgumentException("RSA key must have at least 2048 bits");
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Некорректный публичный ключ JWT: нужен RSA public.pem (BEGIN PUBLIC KEY), соответствующий ключу User", e);
        }
    }
}
