package ru.itis.entity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BlockReasonTest {

    @ParameterizedTest
    @EnumSource(BlockReason.class)
    @DisplayName("У каждой причины есть непустое описание")
    void everyReasonHasDescription(BlockReason reason) {
        assertNotNull(reason.getDescription());
        assertFalse(reason.getDescription().isBlank());
    }

    @ParameterizedTest
    @EnumSource(BlockReason.class)
    @DisplayName("isPermanent() согласован с getDefaultDurationSeconds()")
    void permanentMatchesDuration(BlockReason reason) {
        assertEquals(reason.getDefaultDurationSeconds().isEmpty(), reason.isPermanent());
    }

    @Test
    @DisplayName("Конкретные длительности: 3 дня, 7 дней, 24/48/12 часов")
    void specificDurations() {
        assertEquals(Duration.ofDays(3).toSeconds(),
                BlockReason.RIDE_DOUBLE_RIDING.getDefaultDurationSeconds().orElseThrow());
        assertEquals(Duration.ofDays(7).toSeconds(),
                BlockReason.RIDE_DANGEROUS.getDefaultDurationSeconds().orElseThrow());
        assertEquals(Duration.ofHours(24).toSeconds(),
                BlockReason.PARKING_BLOCKED_PEDESTRIAN.getDefaultDurationSeconds().orElseThrow());
        assertEquals(Duration.ofHours(48).toSeconds(),
                BlockReason.PARKING_FORBIDDEN_ZONE.getDefaultDurationSeconds().orElseThrow());
        assertEquals(Duration.ofHours(12).toSeconds(),
                BlockReason.PARKING_FAKE_PHOTO.getDefaultDurationSeconds().orElseThrow());
    }

    @Test
    @DisplayName("Тяжёлые нарушения блокируют навсегда")
    void seriousViolationsArePermanent() {
        assertTrue(BlockReason.RIDE_INTOXICATED.isPermanent());
        assertTrue(BlockReason.RIDE_ACCIDENT_WITH_PEDESTRIAN.isPermanent());
        assertTrue(BlockReason.ACCOUNT_FAKE_IDENTITY.isPermanent());
        assertTrue(BlockReason.SERVICE_API_ABUSE.isPermanent());
    }

    @Test
    @DisplayName("valueOf с неизвестным именем бросает IllegalArgumentException")
    void valueOfUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> BlockReason.valueOf("UNKNOWN"));
    }
}
