package ru.scooter.user;

import org.junit.jupiter.api.Test;
import ru.scooter.user.exception.ApiException;
import ru.scooter.user.util.BCryptPasswordHasher;
import ru.scooter.user.util.PasswordHasher;
import ru.scooter.user.util.Validation;

import static org.junit.jupiter.api.Assertions.*;

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
    void requiresSixteenYearsAndAcceptsBirthdayBoundary() {
        var boundary = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusYears(16);
        assertEquals(boundary, Validation.birthDate(boundary.toString()));
        assertThrows(ApiException.class, () -> Validation.birthDate(boundary.plusDays(1).toString()));
        assertThrows(ApiException.class, () -> Validation.birthDate(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()));
    }

    @Test
    void validatesNameFormatWithoutPretendingToVerifyIdentity() {
        for (String name : new String[]{"Иван Петров", "Иван Петров Иванович", "Анна-Мария Петрова", "John O’Connor"})
            assertEquals(name, Validation.fullName(name));
        for (String name : new String[]{"huIBSOIAHUD PAISUDB AOSIDHBN", "Иван", "Иван  Петров", "Иван123 Петров", "Иван Pетров", "Иван\nПетров", "иван петров"})
            assertThrows(ApiException.class, () -> Validation.fullName(name), name);
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
