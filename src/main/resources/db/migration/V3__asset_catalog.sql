ALTER TABLE assets DROP CONSTRAINT assets_type_check;
ALTER TABLE assets DROP CONSTRAINT assets_currency_code_check;

UPDATE assets SET asset_type = 'FIAT' WHERE asset_type = 'CURRENCY';

ALTER TABLE assets
    ADD CONSTRAINT assets_type_check CHECK (asset_type IN ('FIAT', 'CRYPTO', 'COMMODITY'));

ALTER TABLE assets
    ADD CONSTRAINT assets_currency_code_check CHECK (
        (asset_type = 'FIAT' AND currency_code IS NOT NULL)
        OR (asset_type <> 'FIAT' AND currency_code IS NULL)
    );

ALTER TABLE assets ADD COLUMN status VARCHAR(32);

UPDATE assets
SET status = CASE WHEN is_active THEN 'ACTIVE' ELSE 'INACTIVE' END;

ALTER TABLE assets ALTER COLUMN status SET NOT NULL;

ALTER TABLE assets
    ADD CONSTRAINT assets_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'));

DROP INDEX assets_type_active_idx;

ALTER TABLE assets DROP COLUMN is_active;

CREATE INDEX assets_type_status_idx ON assets (asset_type, status);

ALTER TABLE assets ADD COLUMN provider VARCHAR(64);

ALTER TABLE assets ADD COLUMN external_id VARCHAR(255);

CREATE UNIQUE INDEX assets_provider_external_id_uq
    ON assets (provider, external_id)
    WHERE provider IS NOT NULL AND external_id IS NOT NULL;
