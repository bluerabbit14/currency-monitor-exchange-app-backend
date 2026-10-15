-- V12: schema foundation for rates ingestion, charts, user settings, alert modes and monetization.
-- Favorites (user_favorites) is the only "saved assets" model; watchlists are removed.

-- 1. Remove the unused watchlist model.
DROP TABLE IF EXISTS watchlist_items;
DROP TABLE IF EXISTS watchlists;

-- 2. exchange_rates: row write time (observed_at = provider time, fetched_at = our fetch time).
ALTER TABLE exchange_rates ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
UPDATE exchange_rates SET updated_at = fetched_at WHERE updated_at IS NULL;
ALTER TABLE exchange_rates ALTER COLUMN updated_at SET NOT NULL;

-- 3. User settings: main (comparison) currency, locale, timezone.
ALTER TABLE users ADD COLUMN base_asset_id UUID;
ALTER TABLE users ADD COLUMN locale VARCHAR(16);
ALTER TABLE users ADD COLUMN timezone VARCHAR(64);
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users
    ADD CONSTRAINT users_base_asset_fk FOREIGN KEY (base_asset_id) REFERENCES assets (id) ON DELETE SET NULL;

-- 4. Chart data: OHLC candles per pair and interval, rolled up from rate_history.
CREATE TABLE rate_candles (
    pair_id         UUID NOT NULL,
    bucket_interval VARCHAR(8) NOT NULL,
    bucket_start    TIMESTAMP WITH TIME ZONE NOT NULL,
    open_rate       NUMERIC(28, 12) NOT NULL,
    high_rate       NUMERIC(28, 12) NOT NULL,
    low_rate        NUMERIC(28, 12) NOT NULL,
    close_rate      NUMERIC(28, 12) NOT NULL,
    sample_count    INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (pair_id, bucket_interval, bucket_start),
    CONSTRAINT rate_candles_pair_fk FOREIGN KEY (pair_id) REFERENCES asset_pairs (id) ON DELETE RESTRICT,
    CONSTRAINT rate_candles_interval_check CHECK (bucket_interval IN ('1M', '5M', '1H', '1D')),
    CONSTRAINT rate_candles_ohlc_check CHECK (
        open_rate > 0
        AND low_rate <= open_rate AND low_rate <= close_rate
        AND high_rate >= open_rate AND high_rate >= close_rate
    )
);

-- 5. Price alerts: percent mode, recurrence, expiry. target_price stays the trigger level
--    (for PERCENT alerts it is derived from reference_price at creation time).
ALTER TABLE price_alerts ADD COLUMN alert_type VARCHAR(16) NOT NULL DEFAULT 'PRICE';
ALTER TABLE price_alerts ADD COLUMN reference_price NUMERIC(28, 12);
ALTER TABLE price_alerts ADD COLUMN percent_threshold NUMERIC(10, 4);
ALTER TABLE price_alerts ADD COLUMN recurrence VARCHAR(16) NOT NULL DEFAULT 'ONCE';
ALTER TABLE price_alerts ADD COLUMN cooldown_seconds INTEGER;
ALTER TABLE price_alerts ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE price_alerts ADD COLUMN last_triggered_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE price_alerts DROP CONSTRAINT price_alerts_status_check;
ALTER TABLE price_alerts
    ADD CONSTRAINT price_alerts_status_check CHECK (status IN ('ACTIVE', 'TRIGGERED', 'CANCELLED', 'PAUSED', 'EXPIRED'));
ALTER TABLE price_alerts
    ADD CONSTRAINT price_alerts_type_check CHECK (alert_type IN ('PRICE', 'PERCENT'));
ALTER TABLE price_alerts
    ADD CONSTRAINT price_alerts_recurrence_check CHECK (recurrence IN ('ONCE', 'REPEATING'));
ALTER TABLE price_alerts
    ADD CONSTRAINT price_alerts_percent_fields_check CHECK (
        alert_type = 'PRICE'
        OR (reference_price IS NOT NULL AND reference_price > 0
            AND percent_threshold IS NOT NULL AND percent_threshold > 0)
    );
ALTER TABLE price_alerts
    ADD CONSTRAINT price_alerts_cooldown_check CHECK (cooldown_seconds IS NULL OR cooldown_seconds >= 0);

CREATE INDEX price_alerts_expires_active_idx
    ON price_alerts (expires_at) WHERE status = 'ACTIVE' AND expires_at IS NOT NULL;

-- 6. Notifications reference the concrete trigger event (outbox pattern).
ALTER TABLE notifications ADD COLUMN alert_event_id UUID;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_alert_event_fk
        FOREIGN KEY (alert_event_id) REFERENCES alert_events (id) ON DELETE SET NULL;
CREATE INDEX notifications_alert_event_id_idx ON notifications (alert_event_id);

-- 7. Session / OTP audit data for abuse detection and "log out other devices".
ALTER TABLE refresh_sessions ADD COLUMN ip_address VARCHAR(45);
ALTER TABLE refresh_sessions ADD COLUMN user_agent VARCHAR(255);
ALTER TABLE otp_verifications ADD COLUMN ip_address VARCHAR(45);

-- 8. Monetization: plans, per-plan entitlements, subscriptions, webhook idempotency.
CREATE TABLE plans (
    id              UUID PRIMARY KEY,
    code            VARCHAR(32) NOT NULL,
    name            VARCHAR(80) NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT plans_code_uq UNIQUE (code)
);
CREATE UNIQUE INDEX plans_one_default_uq ON plans (is_default) WHERE is_default;

CREATE TABLE plan_entitlements (
    plan_id         UUID NOT NULL,
    feature_key     VARCHAR(64) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    limit_value     INTEGER,  -- NULL = unlimited when enabled
    PRIMARY KEY (plan_id, feature_key),
    CONSTRAINT plan_entitlements_plan_fk FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT plan_entitlements_limit_check CHECK (limit_value IS NULL OR limit_value >= 0)
);

CREATE TABLE subscriptions (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL,
    plan_id                 UUID NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    provider                VARCHAR(32) NOT NULL,
    provider_sub_id         VARCHAR(255),
    current_period_start    TIMESTAMP WITH TIME ZONE,
    current_period_end      TIMESTAMP WITH TIME ZONE,
    auto_renew              BOOLEAN NOT NULL DEFAULT TRUE,
    cancelled_at            TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT subscriptions_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT subscriptions_plan_fk FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE RESTRICT,
    CONSTRAINT subscriptions_status_check CHECK (status IN ('ACTIVE', 'TRIAL', 'GRACE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT subscriptions_provider_check CHECK (provider IN ('APPLE', 'GOOGLE', 'STRIPE', 'MANUAL'))
);
CREATE UNIQUE INDEX subscriptions_provider_sub_uq
    ON subscriptions (provider, provider_sub_id) WHERE provider_sub_id IS NOT NULL;
CREATE INDEX subscriptions_user_status_idx ON subscriptions (user_id, status);

CREATE TABLE payment_events (
    id                  UUID PRIMARY KEY,
    subscription_id     UUID,
    provider            VARCHAR(32) NOT NULL,
    provider_event_id   VARCHAR(255) NOT NULL,
    event_type          VARCHAR(64) NOT NULL,
    payload             JSONB NOT NULL,
    processed_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_events_subscription_fk
        FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE SET NULL,
    CONSTRAINT payment_events_provider_event_uq UNIQUE (provider, provider_event_id)
);

-- Seed FREE (default) and PRO. Limits are starting values; tune them with data changes, not code.
INSERT INTO plans (id, code, name, is_default) VALUES
    (gen_random_uuid(), 'FREE', 'Free', TRUE),
    (gen_random_uuid(), 'PRO', 'Pro', FALSE);

INSERT INTO plan_entitlements (plan_id, feature_key, enabled, limit_value)
SELECT p.id, e.feature_key, e.enabled, e.limit_value
FROM plans p
JOIN (VALUES
    ('FREE', 'max_active_alerts',    TRUE,  5),
    ('FREE', 'max_favorites',        TRUE,  10),
    ('FREE', 'max_compare_assets',   TRUE,  2),
    ('FREE', 'chart_max_range_days', TRUE,  30),
    ('FREE', 'min_refresh_seconds',  TRUE,  60),
    ('FREE', 'percent_alerts',       FALSE, NULL),
    ('FREE', 'repeating_alerts',     FALSE, NULL),
    ('PRO',  'max_active_alerts',    TRUE,  100),
    ('PRO',  'max_favorites',        TRUE,  NULL),
    ('PRO',  'max_compare_assets',   TRUE,  10),
    ('PRO',  'chart_max_range_days', TRUE,  NULL),
    ('PRO',  'min_refresh_seconds',  TRUE,  5),
    ('PRO',  'percent_alerts',       TRUE,  NULL),
    ('PRO',  'repeating_alerts',     TRUE,  NULL)
) AS e(plan_code, feature_key, enabled, limit_value) ON e.plan_code = p.code;

-- 9. Provider mapping: Twelve Data for fiat, CoinGecko for crypto.
--    Commodities are undecided and intentionally left NULL.
UPDATE assets
SET provider = 'COINGECKO',
    external_id = CASE symbol
        WHEN 'BTC' THEN 'bitcoin'
        WHEN 'ETH' THEN 'ethereum'
        WHEN 'SOL' THEN 'solana'
        WHEN 'BNB' THEN 'binancecoin'
    END
WHERE asset_type = 'CRYPTO' AND symbol IN ('BTC', 'ETH', 'SOL', 'BNB');

UPDATE asset_pairs p
SET provider_code = 'TWELVEDATA',
    provider_pair_code = b.symbol || '/' || q.symbol,
    updated_at = CURRENT_TIMESTAMP
FROM assets b, assets q
WHERE b.id = p.base_asset_id AND q.id = p.quote_asset_id
  AND b.asset_type = 'FIAT' AND q.asset_type = 'FIAT';

UPDATE asset_pairs p
SET provider_code = 'COINGECKO',
    provider_pair_code = b.external_id || ':' || lower(q.currency_code),
    updated_at = CURRENT_TIMESTAMP
FROM assets b, assets q
WHERE b.id = p.base_asset_id AND q.id = p.quote_asset_id
  AND b.asset_type = 'CRYPTO' AND b.external_id IS NOT NULL
  AND q.asset_type = 'FIAT';
