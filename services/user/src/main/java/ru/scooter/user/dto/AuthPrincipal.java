package ru.scooter.user.dto;

import ru.scooter.user.entity.User;

import java.util.UUID;

public record AuthPrincipal(User user, UUID tokenId, String rawToken) {}
