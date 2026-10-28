package ru.scooter.user.service;

import ru.scooter.user.client.ModerationClient;
import ru.scooter.user.dto.*;
import ru.scooter.user.entity.Role;
import ru.scooter.user.entity.Token;
import ru.scooter.user.entity.User;
import ru.scooter.user.exception.ApiException;
import ru.scooter.user.exception.DuplicateLoginException;
import ru.scooter.user.repository.TokenRepository;
import ru.scooter.user.repository.UserRepository;
import ru.scooter.user.util.JwtUtil;
import ru.scooter.user.util.PasswordHasher;
import ru.scooter.user.util.TokenHash;
import ru.scooter.user.util.Validation;

import java.time.Clock;
import java.util.UUID;

public final class AuthService {
    private final UserRepository users;
    private final TokenRepository tokens;
    private final PasswordHasher passwords;
    private final JwtUtil jwt;
    private final ModerationClient moderation;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            TokenRepository tokens,
            PasswordHasher passwords,
            JwtUtil jwt,
            ModerationClient moderation,
            Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.jwt = jwt;
        this.moderation = moderation;
        this.clock = clock;
        this.dummyHash = passwords.hash(UUID.randomUUID().toString());
    }

    private static ApiException unauthorized() {
        return new ApiException(401, "Неверные данные входа или недействительный токен");
    }

    public UserResponse register(RegisterRequest request) {
        String login = Validation.login(request.login());
        String password = Validation.password(request.password(), true);
        String name = Validation.fullName(request.fullName());
        java.time.LocalDate birthDate = Validation.birthDate(request.birthDate());
        String avatar = Validation.avatar(request.avatarUrl());
        User user =
                new User(
                        UUID.randomUUID(),
                        login,
                        passwords.hash(password),
                        name,
                        birthDate,
                        avatar,
                        Role.USER,
                        clock.instant(),
                        clock.instant(),
                        false,
                        null);
        try {
            users.insert(user);
        } catch (DuplicateLoginException e) {
            throw new ApiException(409, "Логин уже занят");
        }
        return UserResponse.from(user, false);
    }

    public LoginResponse login(LoginRequest request) {
        String login = Validation.login(request.login());
        String password = Validation.password(request.password(), false);
        User user = users.findByLogin(login).orElse(null);
        boolean matches =
                passwords.matches(password, user == null ? dummyHash : user.passwordHash());
        if (user == null || !matches || user.deleted()) throw unauthorized();
        JwtUtil.IssuedToken issued = jwt.issue(user.id(), user.role());
        tokens.insert(
                new Token(
                        issued.id(),
                        user.id(),
                        TokenHash.sha256(issued.value()),
                        issued.issuedAt(),
                        issued.expiresAt(),
                        true));
        try {
            boolean blocked = moderation.isBlocked(user.id(), issued.value());
            return new LoginResponse(
                    issued.value(), jwt.ttlSeconds(), UserResponse.from(user, blocked));
        } catch (RuntimeException e) {
            try {
                tokens.revoke(issued.id());
            } catch (RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    public AuthPrincipal authenticate(String rawToken) {
        JwtUtil.VerifiedToken verified = jwt.verify(rawToken);
        Token token = tokens.findById(verified.tokenId()).orElseThrow(AuthService::unauthorized);
        if (!token.valid()
                || !token.expiresAt().isAfter(clock.instant())
                || !token.userId().equals(verified.userId())
                || !token.tokenHash().equals(TokenHash.sha256(rawToken))) throw unauthorized();
        User user = users.findById(verified.userId()).orElseThrow(AuthService::unauthorized);
        if (user.deleted() || user.role() != verified.role()) throw unauthorized();
        return new AuthPrincipal(user, token.id(), rawToken);
    }

    public void logout(AuthPrincipal principal) {
        tokens.revoke(principal.tokenId());
    }
}
