package ru.itis.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.dao.BlockDao;
import ru.itis.dao.ReasonDao;
import ru.itis.entity.BlockStatus;
import ru.itis.entity.BlockedUser;
import ru.itis.entity.Reason;
import ru.itis.entity.enums.BlockReason;
import ru.itis.exception.ValidationException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModerationServiceImplTest {

    @Mock
    private BlockDao blockDao;

    @Mock
    private ReasonDao reasonDao;

    @InjectMocks
    private ModerationServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("getStatus: пользователя нет в блокировках -> не заблокирован")
    void getStatus_userNotInDb_returnsNotBlocked() {
        when(blockDao.findByUserId(userId)).thenReturn(Optional.empty());

        BlockStatus status = service.getStatus(userId);

        assertFalse(status.isBlocked());
        assertEquals(userId, status.getUserId());
        assertNull(status.getReasonType());
        assertNull(status.getBlockedUntil());
    }

    @Test
    @DisplayName("getStatus: активная временная блокировка -> blocked=true и поля заполнены")
    void getStatus_activeBlock_returnsBlocked() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(1));
        when(blockDao.findByUserId(userId)).thenReturn(Optional.of(
                new BlockedUser(userId, BlockReason.RIDE_DANGEROUS, now, until)));

        BlockStatus status = service.getStatus(userId);

        assertTrue(status.isBlocked());
        assertEquals("RIDE_DANGEROUS", status.getReasonType());
        assertEquals(BlockReason.RIDE_DANGEROUS.getDescription(), status.getReason());
        assertEquals(until, status.getBlockedUntil());
        verify(blockDao, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("getStatus: бессрочная блокировка (blockedUntil=null) остаётся активной")
    void getStatus_permanentBlock_staysBlocked() {
        when(blockDao.findByUserId(userId)).thenReturn(Optional.of(
                new BlockedUser(userId, BlockReason.RIDE_INTOXICATED, Instant.now(), null)));

        BlockStatus status = service.getStatus(userId);

        assertTrue(status.isBlocked());
        assertNull(status.getBlockedUntil());
        verify(blockDao, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("getStatus: срок блокировки истёк -> запись удаляется, статус не заблокирован")
    void getStatus_expiredBlock_deletesAndReturnsNotBlocked() {
        Instant past = Instant.now().minusSeconds(3600);
        when(blockDao.findByUserId(userId)).thenReturn(Optional.of(
                new BlockedUser(userId, BlockReason.PARKING_FAKE_PHOTO, past.minusSeconds(100), past)));

        BlockStatus status = service.getStatus(userId);

        assertFalse(status.isBlocked());
        verify(blockDao).deleteByUserId(userId);
    }

    // ---------- ban ----------

    @Test
    @DisplayName("ban: null / пустая / пробельная причина -> ValidationException")
    void ban_blankReason_throwsValidation() {
        assertThrows(ValidationException.class, () -> service.ban(userId, null));
        assertThrows(ValidationException.class, () -> service.ban(userId, ""));
        assertThrows(ValidationException.class, () -> service.ban(userId, "   "));

        verifyNoInteractions(blockDao);
    }

    @Test
    @DisplayName("ban: неизвестная причина -> ValidationException, в БД ничего не пишется")
    void ban_unknownReason_throwsValidation() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.ban(userId, "NOT_A_REAL_REASON"));

        assertEquals("Неизвестная причина блокировки", ex.getMessage());
        verifyNoInteractions(blockDao);
    }

    @Test
    @DisplayName("ban: временная причина -> blockedUntil = now + длительность причины")
    void ban_temporaryReason_setsBlockedUntil() {
        BlockReason reason = BlockReason.RIDE_DOUBLE_RIDING; // 3 дня
        Instant before = Instant.now();
        stubEmptyThenInserted();

        BlockStatus status = service.ban(userId, reason.name());
        Instant after = Instant.now();

        ArgumentCaptor<BlockedUser> captor = ArgumentCaptor.forClass(BlockedUser.class);
        verify(blockDao).insertBlock(captor.capture());
        BlockedUser saved = captor.getValue();

        assertEquals(userId, saved.getUserId());
        assertEquals(reason, saved.getReasonType());
        long seconds = reason.getDefaultDurationSeconds().orElseThrow();
        assertFalse(saved.getBlockedUntil().isBefore(before.plusSeconds(seconds)));
        assertFalse(saved.getBlockedUntil().isAfter(after.plusSeconds(seconds)));
        assertTrue(status.isBlocked());
    }

    @Test
    @DisplayName("ban: перманентная причина -> blockedUntil = null")
    void ban_permanentReason_blockedUntilIsNull() {
        stubEmptyThenInserted();

        service.ban(userId, BlockReason.VANDALISM_PHYSICAL.name());

        ArgumentCaptor<BlockedUser> captor = ArgumentCaptor.forClass(BlockedUser.class);
        verify(blockDao).insertBlock(captor.capture());
        assertNull(captor.getValue().getBlockedUntil());
    }

    @Test
    @DisplayName("ban: пользователь уже заблокирован -> возвращается текущий статус, новый insert не делается")
    void ban_alreadyBlocked_doesNotInsertAgain() {
        when(blockDao.findByUserId(userId)).thenReturn(Optional.of(
                new BlockedUser(userId, BlockReason.PAYMENT_DEBT, Instant.now(), null)));

        BlockStatus status = service.ban(userId, BlockReason.RIDE_DANGEROUS.name());

        assertTrue(status.isBlocked());
        assertEquals("PAYMENT_DEBT", status.getReasonType()); // причина осталась прежней
        verify(blockDao, never()).insertBlock(any());
    }

    @Test
    @DisplayName("unban: удаляет блокировку и возвращает notBlocked")
    void unban_deletesAndReturnsNotBlocked() {
        BlockStatus status = service.unban(userId);

        verify(blockDao).deleteByUserId(userId);
        assertFalse(status.isBlocked());
        assertEquals(userId, status.getUserId());
    }

    @Test
    @DisplayName("listBlocked: недопустимые limit/offset -> ValidationException")
    void listBlocked_invalidParams_throwsValidation() {
        assertThrows(ValidationException.class, () -> service.listBlocked(0, 0));
        assertThrows(ValidationException.class, () -> service.listBlocked(-5, 0));
        assertThrows(ValidationException.class, () -> service.listBlocked(501, 0));
        assertThrows(ValidationException.class, () -> service.listBlocked(10, -1));

        verifyNoInteractions(blockDao);
    }

    @Test
    @DisplayName("listBlocked: граничные значения limit=1 и limit=500 допустимы")
    void listBlocked_boundaryLimits_ok() {
        when(blockDao.findAll(anyInt(), anyInt())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.listBlocked(1, 0));
        assertDoesNotThrow(() -> service.listBlocked(500, 0));
    }

    @Test
    @DisplayName("listBlocked: DAO получает limit/offset, записи маппятся в BlockStatus")
    void listBlocked_mapsEntitiesToStatuses() {
        UUID second = UUID.randomUUID();
        when(blockDao.findAll(10, 20)).thenReturn(List.of(
                new BlockedUser(userId, BlockReason.RIDE_DANGEROUS, Instant.now(), Instant.now().plusSeconds(60)),
                new BlockedUser(second, BlockReason.PAYMENT_DEBT, Instant.now(), null)));

        List<BlockStatus> result = service.listBlocked(10, 20);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(BlockStatus::isBlocked));
        assertEquals(userId, result.get(0).getUserId());
        assertEquals("PAYMENT_DEBT", result.get(1).getReasonType());
        verify(blockDao).findAll(10, 20);
    }

    @Test
    @DisplayName("getReasons: список сортируется по порядку объявления в enum")
    void getReasons_sortedByEnumOrdinal() {
        when(reasonDao.findAll()).thenReturn(List.of(
                new Reason(BlockReason.SERVICE_API_ABUSE, "d3", null),
                new Reason(BlockReason.RIDE_DOUBLE_RIDING, "d1", 259200L),
                new Reason(BlockReason.PAYMENT_DEBT, "d2", null)));

        List<Reason> result = service.getReasons();

        assertEquals(List.of(
                BlockReason.RIDE_DOUBLE_RIDING,
                BlockReason.PAYMENT_DEBT,
                BlockReason.SERVICE_API_ABUSE),
                result.stream().map(Reason::getReasonType).toList());
    }

    private void stubEmptyThenInserted() {
        AtomicReference<BlockedUser> stored = new AtomicReference<>();
        when(blockDao.findByUserId(userId))
                .thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(blockDao.insertBlock(any(BlockedUser.class))).thenAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return true;
        });
    }
}
