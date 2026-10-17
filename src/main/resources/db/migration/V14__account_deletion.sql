-- V14: account deletion requests.
-- Reconstructed from the live Neon schema: the original file was never committed but had already been applied.
-- Adds PENDING_DELETION to the users status check and creates account_deletion_requests (unused by V15's design).
-- Idempotent so it is a no-op where the original already ran.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users
    ADD CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING_DELETION', 'DELETED'));

CREATE TABLE IF NOT EXISTS account_deletion_requests (
    id                     UUID PRIMARY KEY,
    user_id                UUID NOT NULL,
    status                 VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_for          TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at           TIMESTAMP WITH TIME ZONE,
    processing_started_at  TIMESTAMP WITH TIME ZONE,
    completed_at           TIMESTAMP WITH TIME ZONE,
    attempt_count          INTEGER NOT NULL DEFAULT 0,
    next_attempt_at        TIMESTAMP WITH TIME ZONE,
    last_error             VARCHAR(500),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT account_deletion_requests_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT account_deletion_requests_status_check
        CHECK (status IN ('PENDING', 'CANCELLED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT account_deletion_requests_attempt_check CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS account_deletion_requests_user_idx ON account_deletion_requests (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS account_deletion_requests_open_uq
    ON account_deletion_requests (user_id) WHERE status IN ('PENDING', 'PROCESSING', 'FAILED');

CREATE INDEX IF NOT EXISTS account_deletion_requests_due_idx
    ON account_deletion_requests (scheduled_for) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS account_deletion_requests_retry_idx
    ON account_deletion_requests (next_attempt_at) WHERE status = 'FAILED';
