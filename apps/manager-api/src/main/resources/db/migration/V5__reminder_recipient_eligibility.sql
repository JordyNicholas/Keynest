ALTER TABLE users ADD COLUMN reminder_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN reminder_verified_email VARCHAR(320);
ALTER TABLE users ADD COLUMN reminder_opted_in_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE users ADD CONSTRAINT ck_reminder_verification_pair CHECK (
  (reminder_verified_at IS NULL AND reminder_verified_email IS NULL)
  OR (reminder_verified_at IS NOT NULL AND reminder_verified_email IS NOT NULL)
);

ALTER TABLE users ADD CONSTRAINT ck_reminder_consent_requires_current_address CHECK (
  reminder_opted_in_at IS NULL OR (
    reminder_verified_at IS NOT NULL AND reminder_verified_email IS NOT NULL
    AND reminder_verified_email = email AND reminder_opted_in_at >= reminder_verified_at
  )
);
