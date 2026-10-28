

BEGIN;
ALTER TABLE tokens RENAME COLUMN expires_at TO expire_at;
ALTER
INDEX tokens_expires_at_idx RENAME TO tokens_expire_at_idx;
COMMIT;
