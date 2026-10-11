CREATE TABLE users (
    id                  UUID PRIMARY KEY,
    email               VARCHAR(320),
    phone_number        VARCHAR(20),
    display_name        VARCHAR(100),
    profile_image_url   TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email)) WHERE email IS NOT NULL;

CREATE UNIQUE INDEX users_phone_number_uq ON users (phone_number) WHERE phone_number IS NOT NULL;

CREATE TABLE assets (
    id              UUID PRIMARY KEY,
    symbol          VARCHAR(16) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    asset_type      VARCHAR(32) NOT NULL,
    currency_code   VARCHAR(3),
    decimal_scale   SMALLINT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT assets_symbol_uq UNIQUE (symbol),
    CONSTRAINT assets_type_check CHECK (asset_type IN ('CURRENCY', 'CRYPTO', 'COMMODITY')),
    CONSTRAINT assets_currency_code_check CHECK (
        (asset_type = 'CURRENCY' AND currency_code IS NOT NULL)
        OR (asset_type <> 'CURRENCY' AND currency_code IS NULL)
    )
);

CREATE INDEX assets_type_active_idx ON assets (asset_type, is_active);

CREATE TABLE user_auth_providers (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    provider            VARCHAR(32) NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT user_auth_providers_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_auth_providers_provider_user_uq UNIQUE (provider, provider_user_id),
    CONSTRAINT user_auth_providers_provider_check CHECK (provider IN ('GOOGLE', 'APPLE', 'EMAIL', 'PHONE'))
);

CREATE INDEX user_auth_providers_user_id_idx ON user_auth_providers (user_id);

CREATE TABLE otp_verifications (
    id                  UUID PRIMARY KEY,
    user_id             UUID,
    identifier          VARCHAR(320) NOT NULL,
    identifier_type     VARCHAR(32) NOT NULL,
    purpose             VARCHAR(32) NOT NULL,
    otp_hash            VARCHAR(255) NOT NULL,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at         TIMESTAMP WITH TIME ZONE,
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT otp_verifications_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT otp_verifications_attempt_count_check CHECK (attempt_count >= 0),
    CONSTRAINT otp_verifications_identifier_type_check CHECK (identifier_type IN ('EMAIL', 'PHONE')),
    CONSTRAINT otp_verifications_purpose_check CHECK (purpose IN ('SIGN_UP', 'SIGN_IN', 'VERIFY'))
);

CREATE INDEX otp_verifications_user_id_idx ON otp_verifications (user_id);

CREATE INDEX otp_verifications_lookup_idx
    ON otp_verifications (identifier, identifier_type, purpose, created_at DESC);

CREATE INDEX otp_verifications_expires_unverified_idx
    ON otp_verifications (expires_at)
    WHERE verified_at IS NULL;

CREATE TABLE user_devices (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    device_token    TEXT NOT NULL,
    platform        VARCHAR(32) NOT NULL,
    device_name     VARCHAR(120),
    app_version     VARCHAR(32),
    last_seen_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT user_devices_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_devices_device_token_uq UNIQUE (device_token),
    CONSTRAINT user_devices_platform_check CHECK (platform IN ('ANDROID', 'IOS', 'WEB'))
);

CREATE INDEX user_devices_user_id_idx ON user_devices (user_id);

CREATE TABLE notification_preferences (
    user_id         UUID PRIMARY KEY,
    push_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    sms_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT notification_preferences_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE asset_pairs (
    id              UUID PRIMARY KEY,
    base_asset_id   UUID NOT NULL,
    quote_asset_id  UUID NOT NULL,
    symbol          VARCHAR(32) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT asset_pairs_base_asset_fk FOREIGN KEY (base_asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT asset_pairs_quote_asset_fk FOREIGN KEY (quote_asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT asset_pairs_base_quote_uq UNIQUE (base_asset_id, quote_asset_id),
    CONSTRAINT asset_pairs_symbol_uq UNIQUE (symbol),
    CONSTRAINT asset_pairs_distinct_assets_check CHECK (base_asset_id <> quote_asset_id)
);

CREATE INDEX asset_pairs_quote_asset_id_idx ON asset_pairs (quote_asset_id);

CREATE TABLE exchange_rates (
    pair_id         UUID PRIMARY KEY,
    rate            NUMERIC(28, 12) NOT NULL,
    previous_rate   NUMERIC(28, 12),
    change_percent  NUMERIC(28, 12),
    source          VARCHAR(64) NOT NULL,
    fetched_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT exchange_rates_pair_fk FOREIGN KEY (pair_id) REFERENCES asset_pairs (id) ON DELETE RESTRICT,
    CONSTRAINT exchange_rates_rate_positive_check CHECK (rate > 0)
);

CREATE TABLE rate_history (
    id              UUID PRIMARY KEY,
    pair_id         UUID NOT NULL,
    rate            NUMERIC(28, 12) NOT NULL,
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    source          VARCHAR(64) NOT NULL,
    CONSTRAINT rate_history_pair_fk FOREIGN KEY (pair_id) REFERENCES asset_pairs (id) ON DELETE RESTRICT,
    CONSTRAINT rate_history_pair_recorded_uq UNIQUE (pair_id, recorded_at),
    CONSTRAINT rate_history_rate_positive_check CHECK (rate > 0)
);

CREATE INDEX rate_history_pair_recorded_desc_idx ON rate_history (pair_id, recorded_at DESC);

CREATE TABLE watchlists (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    name            VARCHAR(80) NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT watchlists_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX watchlists_user_id_idx ON watchlists (user_id);

CREATE UNIQUE INDEX watchlists_one_default_uq ON watchlists (user_id) WHERE is_default;

CREATE TABLE watchlist_items (
    id              UUID PRIMARY KEY,
    watchlist_id    UUID NOT NULL,
    asset_id        UUID NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT watchlist_items_watchlist_fk FOREIGN KEY (watchlist_id) REFERENCES watchlists (id) ON DELETE CASCADE,
    CONSTRAINT watchlist_items_asset_fk FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT watchlist_items_watchlist_asset_uq UNIQUE (watchlist_id, asset_id)
);

CREATE INDEX watchlist_items_asset_id_idx ON watchlist_items (asset_id);

CREATE TABLE price_alerts (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    pair_id         UUID NOT NULL,
    condition       VARCHAR(32) NOT NULL,
    target_price    NUMERIC(28, 12) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    triggered_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT price_alerts_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT price_alerts_pair_fk FOREIGN KEY (pair_id) REFERENCES asset_pairs (id) ON DELETE RESTRICT,
    CONSTRAINT price_alerts_target_price_positive_check CHECK (target_price > 0),
    CONSTRAINT price_alerts_condition_check CHECK (condition IN ('ABOVE', 'BELOW')),
    CONSTRAINT price_alerts_status_check CHECK (status IN ('ACTIVE', 'TRIGGERED', 'CANCELLED'))
);

CREATE INDEX price_alerts_user_status_idx ON price_alerts (user_id, status);

CREATE INDEX price_alerts_active_pair_idx ON price_alerts (pair_id) WHERE status = 'ACTIVE';

CREATE TABLE alert_events (
    id                      UUID PRIMARY KEY,
    alert_id                UUID NOT NULL,
    triggered_price         NUMERIC(28, 12) NOT NULL,
    triggered_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    notification_status     VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT alert_events_alert_fk FOREIGN KEY (alert_id) REFERENCES price_alerts (id) ON DELETE CASCADE,
    CONSTRAINT alert_events_notification_status_check CHECK (notification_status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX alert_events_alert_triggered_idx ON alert_events (alert_id, triggered_at DESC);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    alert_id        UUID,
    type            VARCHAR(32) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    sent_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT notifications_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT notifications_alert_fk FOREIGN KEY (alert_id) REFERENCES price_alerts (id) ON DELETE SET NULL,
    CONSTRAINT notifications_type_check CHECK (type IN ('PRICE_ALERT')),
    CONSTRAINT notifications_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX notifications_user_created_idx ON notifications (user_id, created_at DESC);

CREATE INDEX notifications_alert_id_idx ON notifications (alert_id);

CREATE INDEX notifications_pending_idx ON notifications (created_at) WHERE status = 'PENDING';
