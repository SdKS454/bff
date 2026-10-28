package ru.scooter.user.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class GenerateKeys {
    private GenerateKeys() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("Usage: GenerateKeys <new-secrets-directory>");
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        Path privatePath = directory.resolve("private.pem"),
                publicPath = directory.resolve("public.pem");
        if (Files.exists(privatePath) || Files.exists(publicPath))
            throw new IllegalArgumentException("Refusing to overwrite existing keys");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair pair = generator.generateKeyPair();
        write(privatePath, "PRIVATE KEY", pair.getPrivate().getEncoded());
        write(publicPath, "PUBLIC KEY", pair.getPublic().getEncoded());
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            Files.setPosixFilePermissions(
                    privatePath,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        System.out.println(
                "Created RSA keys in " + directory.toAbsolutePath() + "; share only public.pem");
    }

    private static void write(Path path, String type, byte[] bytes) throws java.io.IOException {
        String pem =
                "-----BEGIN "
                        + type
                        + "-----\n"
                        + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(bytes)
                        + "\n-----END "
                        + type
                        + "-----\n";
        Files.writeString(path, pem, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
    }
}
