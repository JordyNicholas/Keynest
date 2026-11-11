ALTER TABLE auth_sessions RENAME COLUMN token TO token_digest;

CREATE INDEX idx_auth_sessions_user_id ON auth_sessions (user_id);
CREATE INDEX idx_vault_items_user_updated ON vault_items (user_id, updated_at DESC);

ALTER TABLE auth_sessions
  ADD CONSTRAINT fk_auth_sessions_user
  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE vault_items
  ADD CONSTRAINT fk_vault_items_user
  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
