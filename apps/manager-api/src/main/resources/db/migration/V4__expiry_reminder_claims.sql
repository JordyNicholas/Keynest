ALTER TABLE expiry_notification_outbox ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE expiry_notification_outbox ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE expiry_notification_outbox ADD COLUMN claim_token UUID;
ALTER TABLE expiry_notification_outbox ADD COLUMN lease_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE expiry_notification_outbox ADD CONSTRAINT ck_expiry_reminder_attempts CHECK (attempts >= 0);

CREATE INDEX idx_expiry_reminder_dispatch
  ON expiry_notification_outbox (status, next_attempt_at, queued_at);
