-- Align asset_pairs with Rate module conventions (status enum, optional provider fields, audit timestamps).
ALTER TABLE asset_pairs ADD COLUMN status VARCHAR(32);

UPDATE asset_pairs
SET status = CASE WHEN is_active THEN 'ACTIVE' ELSE 'INACTIVE' END;

ALTER TABLE asset_pairs ALTER COLUMN status SET NOT NULL;

ALTER TABLE asset_pairs
    ADD CONSTRAINT asset_pairs_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE asset_pairs DROP COLUMN is_active;

ALTER TABLE asset_pairs ADD COLUMN provider_code VARCHAR(64);

ALTER TABLE asset_pairs ADD COLUMN provider_pair_code VARCHAR(64);

ALTER TABLE asset_pairs ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

UPDATE asset_pairs SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE asset_pairs ALTER COLUMN updated_at SET NOT NULL;

-- Align exchange_rates: observed_at + provider_code; drop unused change tracking columns.
ALTER TABLE exchange_rates ADD COLUMN observed_at TIMESTAMP WITH TIME ZONE;

UPDATE exchange_rates SET observed_at = fetched_at WHERE observed_at IS NULL;

ALTER TABLE exchange_rates ALTER COLUMN observed_at SET NOT NULL;

ALTER TABLE exchange_rates RENAME COLUMN source TO provider_code;

ALTER TABLE exchange_rates DROP COLUMN previous_rate;

ALTER TABLE exchange_rates DROP COLUMN change_percent;

ALTER TABLE exchange_rates DROP COLUMN updated_at;

-- Align rate_history: observed_at/fetched_at, provider fields, observation-id dedup.
ALTER TABLE rate_history DROP CONSTRAINT rate_history_pair_recorded_uq;

DROP INDEX rate_history_pair_recorded_desc_idx;

ALTER TABLE rate_history RENAME COLUMN recorded_at TO observed_at;

ALTER TABLE rate_history RENAME COLUMN source TO provider_code;

ALTER TABLE rate_history ADD COLUMN fetched_at TIMESTAMP WITH TIME ZONE;

UPDATE rate_history SET fetched_at = observed_at WHERE fetched_at IS NULL;

ALTER TABLE rate_history ALTER COLUMN fetched_at SET NOT NULL;

ALTER TABLE rate_history ADD COLUMN provider_observation_id VARCHAR(255);

CREATE UNIQUE INDEX rate_history_pair_provider_observation_uq
    ON rate_history (pair_id, provider_code, provider_observation_id)
    WHERE provider_observation_id IS NOT NULL;

CREATE INDEX rate_history_pair_observed_desc_idx
    ON rate_history (pair_id, observed_at DESC);
