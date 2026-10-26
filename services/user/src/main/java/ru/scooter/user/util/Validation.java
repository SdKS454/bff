package ru.scooter.user.util;

import ru.scooter.user.exception.ApiException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public final class Validation {
    private Validation() {}

    public static String login(String value) {
        if (value == null) throw bad("Укажите логин");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]{3,64}"))
            throw bad("Логин: 3–64 символа, латиница, цифры, _, . или -");
        return normalized;
    }

    public static String password(String value, boolean registration) {
        if (value == null
                || value.indexOf(0) >= 0
                || value.isEmpty()
                || value.getBytes(StandardCharsets.UTF_8).length > 72
                || (registration && value.length() < 8))
            throw bad("Пароль: от 8 символов при регистрации, не более 72 байт UTF-8");
        return value;
    }

    public static String fullName(String value) {
        if (value == null
                || value.isBlank()
                || value.trim().length() > 200
                || value.codePoints().anyMatch(Character::isISOControl))
            throw bad("Укажите полное имя длиной до 200 символов");
        return value.trim();
    }

    public static LocalDate birthDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.isAfter(LocalDate.now(java.time.ZoneOffset.UTC))
                    || date.isBefore(LocalDate.of(1900, 1, 1)))
                throw bad("Некорректная дата рождения");
            return date;
        } catch (java.time.format.DateTimeParseException | NullPointerException e) {
            throw bad("Дата рождения должна быть YYYY-MM-DD");
        }
    }

    public static String avatar(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (value.length() > 2048
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
                throw bad("Некорректный avatarUrl");
            return value;
        } catch (IllegalArgumentException e) {
            throw bad("Некорректный avatarUrl");
        }
    }

    public static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw bad("Некорректный UUID");
            return id;
        } catch (IllegalArgumentException e) {
            throw bad("Некорректный UUID");
        }
    }

    public static ApiException bad(String message) {
        return new ApiException(400, message);
    }
}
