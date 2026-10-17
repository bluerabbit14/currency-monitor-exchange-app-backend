-- V14: reconstructed. The original file was never committed but was applied to the Neon database.
-- Adds PENDING_DELETION to the users status check and creates account_deletion_requests (unused by V15's design).
-- Idempotent so it is a no-op where the original already ran.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users
    ADD CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING_DELETION', 'DELETED'));

CREATE TABLE IF NOT EXISTS account_deletion_requests (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users (id),
    status        VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    requested_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at  TIMESTAMP WITH TIME ZONE,
    cancelled_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS account_deletion_requests_user_idx ON account_deletion_requests (user_id);
