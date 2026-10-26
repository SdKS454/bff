package ru.scooter.user.repository;

import ru.scooter.user.dto.UserSummary;
import ru.scooter.user.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    void insert(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByLogin(String login);

    boolean existsByLogin(String login);

    List<UserSummary> findActiveUsers();
}
