CREATE TABLE expiry_notification_outbox (
  vault_item_id UUID NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  queued_at TIMESTAMP WITH TIME ZONE NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  CONSTRAINT pk_expiry_notification_outbox PRIMARY KEY (vault_item_id, expires_at),
  CONSTRAINT fk_expiry_notification_item FOREIGN KEY (vault_item_id)
    REFERENCES vault_items (id) ON DELETE CASCADE
);
