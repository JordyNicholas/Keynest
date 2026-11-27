CREATE INDEX idx_auth_sessions_expiry_id ON auth_sessions (expires_at, id);
