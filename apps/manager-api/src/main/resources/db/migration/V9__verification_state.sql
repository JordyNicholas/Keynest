-- Empty, internal verification state. No signup, mail sender or bootstrap account is enabled.
CREATE TABLE verification_gate (id INTEGER PRIMARY KEY CHECK (id = 1), turn BOOLEAN NOT NULL DEFAULT FALSE);
INSERT INTO verification_gate (id) VALUES (1);

CREATE TABLE verification_recipients (
  recipient_key VARCHAR(320) PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE verification_clients (
  client_key VARCHAR(64) PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE verification_admissions (
  id UUID PRIMARY KEY,
  recipient_key VARCHAR(320) NOT NULL REFERENCES verification_recipients(recipient_key),
  accepted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_verification_admissions_recipient ON verification_admissions(recipient_key, accepted_at);
CREATE INDEX idx_verification_admissions_time ON verification_admissions(accepted_at, id);
CREATE TABLE verification_client_events (
  id UUID PRIMARY KEY,
  client_key VARCHAR(64) NOT NULL REFERENCES verification_clients(client_key),
  accepted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_verification_client_events_key ON verification_client_events(client_key, accepted_at);
CREATE INDEX idx_verification_client_events_time ON verification_client_events(accepted_at, id);

CREATE TABLE verification_challenges (
  id UUID PRIMARY KEY,
  recipient_key VARCHAR(320) NOT NULL REFERENCES verification_recipients(recipient_key),
  delivery_address VARCHAR(320) NOT NULL,
  purpose VARCHAR(32) NOT NULL CHECK (purpose IN ('NEW_ACCOUNT', 'EXISTING_OWNER')),
  owner_id UUID REFERENCES users(id) ON DELETE CASCADE,
  session_digest VARCHAR(64) REFERENCES auth_sessions(token_digest) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  intent_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  token_digest VARCHAR(64) UNIQUE,
  token_expires_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uk_verification_recipient_purpose UNIQUE(recipient_key, purpose),
  CONSTRAINT ck_verification_purpose_binding CHECK (
    (purpose = 'NEW_ACCOUNT' AND owner_id IS NULL AND session_digest IS NULL)
    OR (purpose = 'EXISTING_OWNER' AND owner_id IS NOT NULL AND session_digest IS NOT NULL)),
  CONSTRAINT ck_verification_token_state CHECK (
    (token_digest IS NULL AND token_expires_at IS NULL)
    OR (token_digest IS NOT NULL AND CHAR_LENGTH(token_digest) = 64 AND token_expires_at IS NOT NULL))
);

-- Reference/claim metadata only: no address, password, raw token or MIME payload.
CREATE TABLE verification_delivery_jobs (
  challenge_id UUID PRIMARY KEY REFERENCES verification_challenges(id) ON DELETE CASCADE,
  status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'CLAIMED', 'FINISHED')),
  outcome VARCHAR(16) CHECK (outcome IN ('SENT', 'FAILED', 'AMBIGUOUS', 'SUPPRESSED', 'ABANDONED')),
  claim_id UUID,
  claim_until TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_verification_claim_state CHECK (
    (status = 'CLAIMED' AND claim_id IS NOT NULL AND claim_until IS NOT NULL)
    OR (status <> 'CLAIMED' AND claim_id IS NULL AND claim_until IS NULL))
);
CREATE INDEX idx_verification_jobs_queue ON verification_delivery_jobs(status, created_at, challenge_id);
