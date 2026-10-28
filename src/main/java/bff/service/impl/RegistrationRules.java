package bff.service.impl;

import bff.dto.AuthRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

/** Mirrors the supplied User Service's Validation; User remains the authority. */
public final class RegistrationRules {
    private RegistrationRules() { }
    public static AuthRequest validate(AuthRequest input) {
        String login = input.login() == null ? "" : input.login().trim().toLowerCase(Locale.ROOT);
        if (!login.matches("[a-z0-9_.-]{3,64}"))
            throw new IllegalArgumentException("Логин: 3–64 символа, латиница, цифры, _, . или -");
        String password = input.password();
        if (password == null || password.length() < 8 || password.indexOf(0) >= 0
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Пароль: от 8 символов при регистрации, не более 72 байт UTF-8");
        String name = fullName(input.fullName());
        try {
            LocalDate date = LocalDate.parse(input.birthDate());
            if (date.isBefore(LocalDate.of(1900, 1, 1)) || date.isAfter(LocalDate.now(ZoneOffset.UTC)))
                throw new IllegalArgumentException("Некорректная дата рождения");
            if (date.isAfter(LocalDate.now(ZoneOffset.UTC).minusYears(16)))
                throw new IllegalArgumentException("Регистрация доступна с 16 лет");
        } catch (java.time.format.DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Дата рождения должна быть YYYY-MM-DD", e);
        }
        return new AuthRequest(login, password, name.trim(), input.birthDate(), input.avatarUrl());
    }
    public static String fullName(String value) {
        String message = "ФИО: 2–4 слова с заглавной буквы, кириллица или латиница, до 200 символов";
        if (value == null || value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(message);
        String name = value.trim();
        String word = "[\\p{Lu}][\\p{Ll}]*(?:[-'’][\\p{Lu}][\\p{Ll}]*)*";
        if (name.length() > 200 || !name.matches(word + "(?: " + word + "){1,3}")) throw new IllegalArgumentException(message);
        Character.UnicodeScript script = null;
        for (String part : name.split(" ")) {
            if (part.codePoints().filter(Character::isLetter).count() < 2) throw new IllegalArgumentException(message);
            for (int point : part.codePoints().filter(Character::isLetter).toArray()) {
                var current = Character.UnicodeScript.of(point);
                if (current != Character.UnicodeScript.CYRILLIC && current != Character.UnicodeScript.LATIN)
                    throw new IllegalArgumentException(message);
                if (script != null && script != current) throw new IllegalArgumentException(message);
                script = current;
            }
        }
        return name;
    }

}
