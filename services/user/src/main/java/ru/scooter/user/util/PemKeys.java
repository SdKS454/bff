package ru.scooter.user.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PemKeys {
    private PemKeys() {
    }

    public static PrivateKey privateKey(Path file) throws IOException, GeneralSecurityException {
        PrivateKey key =
                KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(decode(file, "PRIVATE KEY")));
        check(key);
        return key;
    }

    public static PublicKey publicKey(Path file) throws IOException, GeneralSecurityException {
        PublicKey key =
                KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(decode(file, "PUBLIC KEY")));
        check(key);
        return key;
    }

    private static byte[] decode(Path file, String type) throws IOException {
        String value = Files.readString(file).trim();
        String begin = "-----BEGIN " + type + "-----", end = "-----END " + type + "-----";
        if (!value.startsWith(begin) || !value.endsWith(end))
            throw new IllegalArgumentException("Invalid PEM format: " + file);
        return Base64.getDecoder()
                .decode(
                        value.substring(begin.length(), value.length() - end.length())
                                .replaceAll("\\s", ""));
    }

    private static void check(Key key) {
        if (!(key instanceof RSAKey rsa) || rsa.getModulus().bitLength() < 2048)
            throw new IllegalArgumentException("RSA key must be at least 2048 bits");
    }
}
