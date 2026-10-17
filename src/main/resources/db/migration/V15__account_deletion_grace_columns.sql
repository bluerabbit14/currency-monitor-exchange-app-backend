-- V15: account deletion grace period, tracked on the users row.
-- ACTIVE -> PENDING_DELETION (user asked to delete; logging in again cancels it)
--        -> DELETED (grace period over: personal data erased, payment records kept on an anonymized row).
-- V14 already added PENDING_DELETION to the status check and created account_deletion_requests, which this
-- design does not use; the constraint is re-created below so this file stays correct on its own.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users
    ADD CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING_DELETION', 'DELETED'));

ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_scheduled_at TIMESTAMP WITH TIME ZONE;

-- Profiles soft-deleted by the old behaviour never expired; start their grace period now.
UPDATE users
SET status = 'PENDING_DELETION',
    deletion_requested_at = updated_at,
    deletion_scheduled_at = CURRENT_TIMESTAMP + INTERVAL '15 days'
WHERE status = 'DELETED';

CREATE INDEX IF NOT EXISTS users_pending_deletion_idx
    ON users (deletion_scheduled_at) WHERE status = 'PENDING_DELETION';
