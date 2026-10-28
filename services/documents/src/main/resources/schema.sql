


CREATE TABLE IF NOT EXISTS documents
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),


    user_id UUID NOT NULL,


    document_type VARCHAR
(
    50
) NOT NULL CHECK
(
    document_type
    IN
(
    'RIDE_PAYMENT',
    'USER_BLOCKED',
    'USER_HISTORY'
)),


    source_id UUID,


    period_from DATE,
    period_to DATE,


    status VARCHAR
(
    20
) NOT NULL DEFAULT 'PENDING' CHECK
(
    status
    IN
(
    'PENDING',
    'GENERATED',
    'FAILED'
)),


    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generated_at TIMESTAMP WITH TIME ZONE,

    payload_json JSONB,


    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
                             );


CREATE INDEX IF NOT EXISTS idx_documents_user_id ON documents(user_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_documents_type ON documents(document_type);
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status);
CREATE INDEX IF NOT EXISTS idx_documents_source_id ON documents(source_id);