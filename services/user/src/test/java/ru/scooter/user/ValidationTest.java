package ru.scooter.user;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ru.scooter.user.exception.ApiException;
import ru.scooter.user.util.*;

class ValidationTest {
    @Test
    void bcryptByteLimitAndNulAreEnforced() {
        assertThrows(ApiException.class, () -> Validation.password("я".repeat(37), true));
        assertThrows(ApiException.class, () -> Validation.password("password\0other", true));
        assertEquals("я".repeat(36), Validation.password("я".repeat(36), true));
    }

    @Test
    void normalizesLoginAndRejectsInvalidProfile() {
        assertEquals("ivan", Validation.login(" IVAN "));
        assertThrows(ApiException.class, () -> Validation.login("a' OR 1=1"));
        assertThrows(ApiException.class, () -> Validation.avatar("javascript:alert(1)"));
        assertThrows(ApiException.class, () -> Validation.birthDate("2000-02-30"));
        assertThrows(ApiException.class, () -> Validation.uuid("1-1-1-1-1"));
    }

    @Test
    void bcryptWorksWithUtf8() {
        PasswordHasher hasher = new BCryptPasswordHasher(10);
        String hash = hasher.hash("пароль123");
        assertTrue(hasher.matches("пароль123", hash));
        assertFalse(hasher.matches("пароль124", hash));
        assertFalse(hasher.matches("пароль123", "invalid-hash"));
    }
}
