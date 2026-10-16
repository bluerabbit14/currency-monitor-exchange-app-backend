-- V13: OTP challenges now live in Redis (TTL = OTP expiry), so the table is removed.
-- system_logs is an append-only event trail across all delivery channels.

DROP TABLE IF EXISTS otp_verifications;

CREATE TABLE system_logs (
    id                      UUID PRIMARY KEY,
    event_type              VARCHAR(64) NOT NULL,   -- e.g. OTP_SENT, REGISTRATION_COMPLETED
    category                VARCHAR(32) NOT NULL,   -- AUTH, USER, ALERT, NOTIFICATION, SYSTEM
    status                  VARCHAR(16) NOT NULL,
    channel                 VARCHAR(16),            -- NULL when the event is not a message delivery
    user_id                 UUID,
    recipient               VARCHAR(320),           -- email address, phone number or device token
    provider                VARCHAR(32),            -- RESEND, FCM, APNS, SMS gateway, ...
    provider_message_id     VARCHAR(255),
    reference_type          VARCHAR(48),            -- e.g. NOTIFICATION, PRICE_ALERT, ALERT_EVENT
    reference_id            VARCHAR(64),
    error_code              VARCHAR(64),
    error_message           TEXT,
    ip_address              VARCHAR(45),
    metadata                JSONB,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT system_logs_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT system_logs_status_check CHECK (status IN ('SUCCESS', 'FAILURE', 'SKIPPED')),
    CONSTRAINT system_logs_channel_check CHECK (channel IS NULL OR channel IN ('EMAIL', 'PUSH', 'IN_APP', 'SMS'))
);

CREATE INDEX system_logs_user_created_idx ON system_logs (user_id, created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX system_logs_event_created_idx ON system_logs (event_type, created_at DESC);
CREATE INDEX system_logs_channel_status_created_idx
    ON system_logs (channel, status, created_at DESC) WHERE channel IS NOT NULL;
CREATE INDEX system_logs_recipient_created_idx
    ON system_logs (recipient, created_at DESC) WHERE recipient IS NOT NULL;
CREATE INDEX system_logs_reference_idx
    ON system_logs (reference_type, reference_id) WHERE reference_id IS NOT NULL;
CREATE INDEX system_logs_created_idx ON system_logs (created_at DESC);
