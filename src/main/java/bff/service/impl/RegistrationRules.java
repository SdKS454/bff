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
        String name = input.fullName();
        if (name == null || name.isBlank() || name.trim().length() > 200
                || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Укажите полное имя длиной до 200 символов");
        try {
            LocalDate date = LocalDate.parse(input.birthDate());
            if (date.isBefore(LocalDate.of(1900, 1, 1)) || date.isAfter(LocalDate.now(ZoneOffset.UTC)))
                throw new IllegalArgumentException("Некорректная дата рождения");
        } catch (java.time.format.DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Дата рождения должна быть YYYY-MM-DD", e);
        }
        return new AuthRequest(login, password, name.trim(), input.birthDate(), input.avatarUrl());
    }
}
