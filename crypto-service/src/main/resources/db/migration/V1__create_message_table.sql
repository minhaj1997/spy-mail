CREATE TABLE message (
  id              UUID        PRIMARY KEY,
  iv              BYTEA       NOT NULL,
  ciphertext      BYTEA       NOT NULL,
  failed_attempts INTEGER     NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Used later by the cleanup job that deletes old messages.
CREATE INDEX message_created_at_idx ON message (created_at);
