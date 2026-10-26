package ru.scooter.user.util;

import org.mindrot.jbcrypt.BCrypt;

public final class BCryptPasswordHasher implements PasswordHasher {
    private final int cost;

    public BCryptPasswordHasher(int cost) {
        this.cost = cost;
    }

    @Override
    public String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(cost));
    }

    @Override
    public boolean matches(String password, String hash) {
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
