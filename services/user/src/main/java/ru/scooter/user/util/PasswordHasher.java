package ru.scooter.user.util;

public interface PasswordHasher {
    String hash(String password);

    boolean matches(String password, String hash);
}
