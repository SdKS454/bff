CREATE TABLE IF NOT EXISTS reasons
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    type VARCHAR
(
    50
) UNIQUE NOT NULL,
    description TEXT NOT NULL,
    default_duration_seconds BIGINT DEFAULT NULL
    );

CREATE TABLE IF NOT EXISTS blocked_users
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    user_id UUID UNIQUE NOT NULL,
    reason_id UUID NOT NULL REFERENCES reasons
(
    id
) ON DELETE RESTRICT,
    blocked_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    blocked_until TIMESTAMPTZ DEFAULT NULL
    );

CREATE INDEX IF NOT EXISTS idx_blocked_users_blocked_until ON blocked_users(blocked_until);

INSERT INTO reasons (type, description, default_duration_seconds)
VALUES ('RIDE_DOUBLE_RIDING', 'Езда вдвоем или втроем', 259200),
       ('RIDE_UNDERAGE', 'Передача самоката несовершеннолетнему', NULL),
       ('RIDE_INTOXICATED', 'Езда в состоянии опьянения', NULL),
       ('RIDE_DANGEROUS', 'Агрессивное / опасное вождение', 604800),
       ('RIDE_ACCIDENT_WITH_PEDESTRIAN', 'Наезд на пешехода / ДТП', NULL),
       ('VANDALISM_PHYSICAL', 'Умышленная порча техники', NULL),
       ('STOLEN_OR_ABANDONED', 'Попытка кражи или удержания техники', NULL),
       ('HEAVY_OVERLOAD', 'Превышение допустимой нагрузки', 259200),
       ('PARKING_BLOCKED_PEDESTRIAN', 'Парковка, создающая препятствия', 86400),
       ('PARKING_FORBIDDEN_ZONE', 'Оставление в запрещенной зоне', 172800),
       ('PARKING_FAKE_PHOTO', 'Фиктивное / некачественное фото парковки', 43200),
       ('PAYMENT_DEBT', 'Неоплаченная задолженность', NULL),
       ('PAYMENT_CHARGEBACK_FRAUD', 'Оспаривание платежей через банк', NULL),
       ('PAYMENT_INVALID_CARD', 'Использование подозрительных карт', NULL),
       ('ACCOUNT_MULTI_ACCOUNTING', 'Мультиаккаунтинг', NULL),
       ('ACCOUNT_FAKE_IDENTITY', 'Использование чужих документов', NULL),
       ('ACCOUNT_TRANSFER', 'Передача аккаунта третьим лицам', 604800),
       ('SERVICE_ABUSE_SUPPORT', 'Оскорбление службы поддержки', 604800),
       ('SERVICE_API_ABUSE', 'Попытка взлома / использование ботов', NULL) ON CONFLICT (type) DO NOTHING;
