package ru.scooter.user.config;

import ru.scooter.user.service.*;

public record AppComponents(
        AppConfig config, AuthService auth, UserService users, ModerationService moderation) {}
