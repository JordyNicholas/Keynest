ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN delivery_email VARCHAR(320);
ALTER TABLE users ADD COLUMN legacy_access_granted BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve only owners present at the upgrade. Mailbox proof is not inferred.
UPDATE users SET legacy_access_granted = TRUE;

ALTER TABLE users ADD CONSTRAINT ck_users_activation_eligible
  CHECK (email_verified_at IS NOT NULL OR legacy_access_granted);
