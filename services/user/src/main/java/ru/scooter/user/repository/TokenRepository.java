package ru.scooter.user.repository;

import ru.scooter.user.entity.Token;

import java.util.Optional;
import java.util.UUID;

public interface TokenRepository {
    void insert(Token token);

    Optional<Token> findById(UUID id);

    void revoke(UUID id);
}
