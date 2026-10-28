package bff;

import bff.dto.AuthRequest;
import bff.service.impl.RegistrationRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationRulesTest {
    AuthRequest request(String login, String password, String fullName, String birthDate) {
        return new AuthRequest(login, password, fullName, birthDate, null);
    }
    @Test void normalizesLikeUserService() {
        var normalized = RegistrationRules.validate(request(" IVAN_1 ", "password", " Иван Петров ", "2000-05-17"));
        assertEquals("ivan_1", normalized.login());
        assertEquals("Иван Петров", normalized.fullName());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"ab", "иван", "a/b", "a b"})
    void rejectsInvalidLogin(String value) {
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request(value, "password", "Иван Петров", "2000-05-17")));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"short", "1234567", "pass\u0000word"})
    void rejectsInvalidPassword(String value) {
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request("ivan", value, "Иван Петров", "2000-05-17")));
    }
    @Test void passwordLimitIsBytesNotCharacters() {
        assertDoesNotThrow(() -> RegistrationRules.validate(request("ivan", "я".repeat(36), "Иван Петров", "2000-05-17")));
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request("ivan", "я".repeat(37), "Иван Петров", "2000-05-17")));
        assertDoesNotThrow(() -> RegistrationRules.validate(request("ivan", "a".repeat(72), "Иван Петров", "2000-05-17")));
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request("ivan", "a".repeat(73), "Иван Петров", "2000-05-17")));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "Иван\nПетров", "Иван", "huIBSOIAHUD PAISUDB AOSIDHBN", "Иван123 Петров", "Иван Pетров", "Иван  Петров"})
    void rejectsInvalidFullName(String value) {
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request("ivan", "password", value, "2000-05-17")));
    }
    @Test void enforcesLoginAndNameUpperBounds() {
        assertDoesNotThrow(() -> RegistrationRules.validate(request("a".repeat(64), "password", "A" + "a".repeat(98) + " B" + "b".repeat(99), "2000-05-17")));
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request("a".repeat(65), "password", "Иван Петров", "2000-05-17")));
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request("ivan", "password", "a".repeat(201), "2000-05-17")));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"1899-12-31", "2026-02-30", "01.01.2000"})
    void birthDateIsRequiredAndValid(String value) {
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request("ivan", "password", "Иван Петров", value)));
    }
    @Test void rejectsFutureBirthday() {
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request("ivan", "password", "Иван Петров", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString())));
    }
    @Test void permitsRegistrationExactlyOnSixteenthBirthday() {
        String boundary = LocalDate.now(ZoneOffset.UTC).minusYears(16).toString();
        assertDoesNotThrow(() -> RegistrationRules.validate(request("ivan", "password", "Иван Петров", boundary)));
        assertThrows(IllegalArgumentException.class, () -> RegistrationRules.validate(request("ivan", "password", "Иван Петров", LocalDate.parse(boundary).plusDays(1).toString())));
    }
    @Test void permitsCompoundAndLatinNames() {
        for (String name : new String[]{"Анна-Мария Петрова", "John O’Connor", "Иван Петров Иванович"})
            assertDoesNotThrow(() -> RegistrationRules.validate(request("ivan", "password", name, "2000-05-17")));
    }
}
