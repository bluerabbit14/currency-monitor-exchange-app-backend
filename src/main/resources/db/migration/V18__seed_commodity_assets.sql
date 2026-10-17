-- Supported commodities: XAU (Gold), XAG (Silver), XPT (Platinum), XPD (Palladium), HG (Copper).
-- Gold and Silver were seeded earlier under the symbols GOLD and SILVER. They are renamed in place (same ids,
-- so favorites and alerts keep pointing at them) rather than duplicated. No price provider is mapped yet,
-- so provider_code / provider_pair_code stay NULL and these pairs are not collected.

UPDATE assets
SET symbol = 'XAU', updated_at = CURRENT_TIMESTAMP
WHERE symbol = 'GOLD' AND asset_type = 'COMMODITY'
  AND NOT EXISTS (SELECT 1 FROM assets a WHERE a.symbol = 'XAU');

UPDATE assets
SET symbol = 'XAG', updated_at = CURRENT_TIMESTAMP
WHERE symbol = 'SILVER' AND asset_type = 'COMMODITY'
  AND NOT EXISTS (SELECT 1 FROM assets a WHERE a.symbol = 'XAG');

UPDATE asset_pairs
SET symbol = 'XAU/USD', updated_at = CURRENT_TIMESTAMP
WHERE symbol = 'GOLD/USD'
  AND NOT EXISTS (SELECT 1 FROM asset_pairs p WHERE p.symbol = 'XAU/USD');

UPDATE asset_pairs
SET symbol = 'XAG/USD', updated_at = CURRENT_TIMESTAMP
WHERE symbol = 'SILVER/USD'
  AND NOT EXISTS (SELECT 1 FROM asset_pairs p WHERE p.symbol = 'XAG/USD');

INSERT INTO assets (
    id, symbol, name, asset_type, currency_code, decimal_scale, status, provider, external_id, created_at, updated_at
)
SELECT gen_random_uuid(), v.symbol, v.name, 'COMMODITY', NULL, 2, 'ACTIVE', NULL, NULL,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('XAU', 'Gold'),
    ('XAG', 'Silver'),
    ('XPT', 'Platinum'),
    ('XPD', 'Palladium'),
    ('HG', 'Copper')
) AS v(symbol, name)
WHERE NOT EXISTS (SELECT 1 FROM assets a WHERE a.symbol = v.symbol);

-- A USD pair for each commodity that does not have one yet (XAU and XAG already do).
INSERT INTO asset_pairs (
    id, base_asset_id, quote_asset_id, symbol, status, provider_code, provider_pair_code, created_at, updated_at
)
SELECT gen_random_uuid(), base.id, usd.id, base.symbol || '/USD', 'ACTIVE', NULL, NULL,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM assets base
JOIN assets usd ON usd.symbol = 'USD' AND usd.asset_type = 'FIAT'
WHERE base.asset_type = 'COMMODITY'
  AND base.symbol IN ('XAU', 'XAG', 'XPT', 'XPD', 'HG')
  AND NOT EXISTS (
      SELECT 1 FROM asset_pairs p WHERE p.base_asset_id = base.id AND p.quote_asset_id = usd.id
  );
