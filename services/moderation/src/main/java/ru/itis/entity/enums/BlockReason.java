package ru.itis.entity.enums;

import java.time.Duration;
import java.util.Optional;

public enum BlockReason {

    RIDE_DOUBLE_RIDING("Езда вдвоем или втроем", Duration.ofDays(3).toSeconds()),
    RIDE_UNDERAGE("Передача самоката несовершеннолетнему", null),
    RIDE_INTOXICATED("Езда в состоянии опьянения", null),
    RIDE_DANGEROUS("Агрессивное / опасное вождение", Duration.ofDays(7).toSeconds()),
    RIDE_ACCIDENT_WITH_PEDESTRIAN("Наезд на пешехода / ДТП", null),

    VANDALISM_PHYSICAL("Умышленная порча техники", null),
    STOLEN_OR_ABANDONED("Попытка кражи или удержания техники", null),
    HEAVY_OVERLOAD("Превышение допустимой нагрузки", Duration.ofDays(3).toSeconds()),

    PARKING_BLOCKED_PEDESTRIAN("Парковка, создающая препятствия", Duration.ofHours(24).toSeconds()),
    PARKING_FORBIDDEN_ZONE("Оставление в запрещенной зоне", Duration.ofHours(48).toSeconds()),
    PARKING_FAKE_PHOTO("Фиктивное / некачественное фото парковки", Duration.ofHours(12).toSeconds()),

    PAYMENT_DEBT("Неоплаченная задолженность", null),
    PAYMENT_CHARGEBACK_FRAUD("Оспаривание платежей через банк", null),
    PAYMENT_INVALID_CARD("Использование подозрительных карт", null),

    ACCOUNT_MULTI_ACCOUNTING("Мультиаккаунтинг", null),
    ACCOUNT_FAKE_IDENTITY("Использование чужих документов", null),
    ACCOUNT_TRANSFER("Передача аккаунта третьим лицам", Duration.ofDays(7).toSeconds()),

    SERVICE_ABUSE_SUPPORT("Оскорбление службы поддержки", Duration.ofDays(7).toSeconds()),
    SERVICE_API_ABUSE("Попытка взлома / использование ботов", null);

    private final String description;
    private final Long defaultDurationSeconds;

    BlockReason(String description, Long defaultDurationSeconds) {
        this.description = description;
        this.defaultDurationSeconds = defaultDurationSeconds;
    }

    public String getDescription() {
        return description;
    }

    public Optional<Long> getDefaultDurationSeconds() {
        return Optional.ofNullable(defaultDurationSeconds);
    }

    public boolean isPermanent() {
        return defaultDurationSeconds == null;
    }
}
