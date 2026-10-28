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
        String message = "ФИО: 2–4 слова с заглавной буквы, кириллица или латиница, до 200 символов";
        if (value == null || value.codePoints().anyMatch(Character::isISOControl)) throw bad(message);
        String name = value.trim();
        String word = "[\\p{Lu}][\\p{Ll}]*(?:[-'’][\\p{Lu}][\\p{Ll}]*)*";
        if (name.length() > 200 || !name.matches(word + "(?: " + word + "){1,3}")) throw bad(message);
        Character.UnicodeScript script = null;
        for (String part : name.split(" ")) {
            if (part.codePoints().filter(Character::isLetter).count() < 2) throw bad(message);
            for (int point : part.codePoints().filter(Character::isLetter).toArray()) {
                var current = Character.UnicodeScript.of(point);
                if (current != Character.UnicodeScript.CYRILLIC && current != Character.UnicodeScript.LATIN)
                    throw bad(message);
                if (script != null && script != current) throw bad(message);
                script = current;
            }
        }
        return name;
    }

    public static LocalDate birthDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.isAfter(LocalDate.now(java.time.ZoneOffset.UTC))
                    || date.isBefore(LocalDate.of(1900, 1, 1)))
                throw bad("Некорректная дата рождения");
            if (date.isAfter(LocalDate.now(java.time.ZoneOffset.UTC).minusYears(16)))
                throw bad("Регистрация доступна с 16 лет");
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
