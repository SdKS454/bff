package ru.scooter.user.config;

import ru.scooter.user.service.AuthService;
import ru.scooter.user.service.ModerationService;
import ru.scooter.user.service.UserService;

public record AppComponents(
        AppConfig config, AuthService auth, UserService users, ModerationService moderation) {
}
