CREATE TABLE users (
  id UUID NOT NULL,
  email VARCHAR(320) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT pk_users PRIMARY KEY (id),
  CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE auth_sessions (
  id UUID NOT NULL,
  user_id UUID NOT NULL,
  token VARCHAR(64) NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT pk_auth_sessions PRIMARY KEY (id),
  CONSTRAINT uk_auth_sessions_token UNIQUE (token)
);

CREATE TABLE vault_items (
  id UUID NOT NULL,
  user_id UUID NOT NULL,
  label VARCHAR(200) NOT NULL,
  url VARCHAR(2000),
  username VARCHAR(320),
  envelope_bytes BYTEA NOT NULL,
  policy_id VARCHAR(120) NOT NULL,
  aad_version INTEGER,
  expires_at TIMESTAMP WITH TIME ZONE,
  last_rotated_at TIMESTAMP WITH TIME ZONE,
  notify_before_days INTEGER,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT pk_vault_items PRIMARY KEY (id)
);
