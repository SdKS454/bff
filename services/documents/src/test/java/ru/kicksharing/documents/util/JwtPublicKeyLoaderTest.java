package ru.kicksharing.documents.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtPublicKeyLoaderTest {
    @TempDir
    Path directory;

    @Test
    void loadsTheSamePublicKeyFromAFileInlineAndEscapedEnvironmentValue() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var expected = generator.generateKeyPair().getPublic();
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(expected.getEncoded()) + "\n-----END PUBLIC KEY-----\n";
        Path path = directory.resolve("Ключ с пробелами.pem");
        Files.writeString(path, "\ufeff" + pem);
        assertEquals(expected, JwtPublicKeyLoader.load(null, path.toString()));
        assertEquals(expected, JwtPublicKeyLoader.load(pem, null));
        assertEquals(expected, JwtPublicKeyLoader.load(pem.replace("\n", "\\n"), null));
    }

    @Test
    void distinguishesMissingConfigUnreadableFileAndInvalidKey() throws Exception {
        assertTrue(assertThrows(IllegalStateException.class, () -> JwtPublicKeyLoader.load(null, " ")).getMessage().contains("не задан"));
        assertTrue(assertThrows(IllegalStateException.class, () -> JwtPublicKeyLoader.load(null, directory.resolve("missing.pem").toString())).getMessage().contains("не может прочитать"));
        Path path = directory.resolve("invalid.pem");
        Files.writeString(path, "not a public key");
        assertTrue(assertThrows(IllegalStateException.class, () -> JwtPublicKeyLoader.load(null, path.toString())).getMessage().contains("Некорректный"));
    }
}
