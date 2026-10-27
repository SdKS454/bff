CREATE INDEX IF NOT EXISTS documents_owner_history ON documents(user_id,created_at DESC,id DESC) WHERE NOT is_deleted;
