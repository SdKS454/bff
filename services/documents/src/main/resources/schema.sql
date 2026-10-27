-- Скрипт создания таблицы для Documents Service
-- Выполняется один раз при инициализации БД (можно через Flyway или вручную)

CREATE TABLE IF NOT EXISTS documents (
                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ID пользователя, для которого сгенерирован документ
    user_id UUID NOT NULL,

    -- Тип документа: RIDE_PAYMENT, USER_BLOCKED, USER_HISTORY
    document_type VARCHAR(50) NOT NULL CHECK (document_type IN ('RIDE_PAYMENT', 'USER_BLOCKED', 'USER_HISTORY')),

    -- ID источника (например, ID поездки для чека). NULL для USER_BLOCKED и USER_HISTORY
    source_id UUID,

    -- Период для USER_HISTORY (даты начала и конца)
    period_from DATE,
    period_to DATE,

    -- Статус генерации: PENDING, GENERATED, FAILED
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'GENERATED', 'FAILED')),

    -- Временные метки
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generated_at TIMESTAMP WITH TIME ZONE,

    payload_json JSONB,

    -- Soft delete
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
                             );

-- Индексы для ускорения поиска (мы будем часто искать по user_id и статусу)
CREATE INDEX IF NOT EXISTS idx_documents_user_id ON documents(user_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_documents_type ON documents(document_type);
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status);
CREATE INDEX IF NOT EXISTS idx_documents_source_id ON documents(source_id);