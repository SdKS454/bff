BEGIN;
CREATE TABLE users (
    id UUID PRIMARY KEY,
    login VARCHAR(64) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    birth_date DATE NOT NULL,
    avatar_url VARCHAR(2048),
    role VARCHAR(5) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT users_login_format CHECK (login ~ '^[a-z0-9_.-]{3,64}$'),
    CONSTRAINT users_full_name CHECK (length(trim(full_name)) > 0),
    CONSTRAINT users_deleted_consistency CHECK (is_deleted = (deleted_at IS NOT NULL))
);
CREATE UNIQUE INDEX users_login_unique ON users (login);
CREATE TABLE tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    expire_at TIMESTAMPTZ NOT NULL,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT tokens_time_order CHECK (expire_at > created_at)
);
CREATE INDEX tokens_user_id_idx ON tokens (user_id);
CREATE INDEX tokens_expire_at_idx ON tokens (expire_at);
CREATE FUNCTION set_user_updated_at() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;
CREATE TRIGGER users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_user_updated_at();
COMMIT;
