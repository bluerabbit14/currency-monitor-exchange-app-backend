-- Supported fiat currencies: USD, EUR, GBP, INR, JPY (already seeded) plus CNY and AED.
-- Each non-USD fiat gets one canonical pair against USD, collected from Twelve Data. Fiat/fiat cross rates
-- (for example EUR/INR) are not stored; they are derived at read time from these USD pairs.
-- Existing pairs keep their direction (USD/INR, EUR/USD, GBP/USD). New pairs use the market convention:
-- USD/JPY, USD/CNY, USD/AED. Idempotent: existing assets and pairs are reused, never duplicated.

INSERT INTO assets (
    id, symbol, name, asset_type, currency_code, decimal_scale, status, provider, external_id, created_at, updated_at
)
SELECT gen_random_uuid(), v.symbol, v.name, 'FIAT', v.symbol, 2, 'ACTIVE', NULL, NULL,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CNY', 'Chinese Yuan'),
    ('AED', 'UAE Dirham')
) AS v(symbol, name)
WHERE NOT EXISTS (SELECT 1 FROM assets a WHERE a.symbol = v.symbol);

INSERT INTO asset_pairs (
    id, base_asset_id, quote_asset_id, symbol, status, provider_code, provider_pair_code, created_at, updated_at
)
SELECT gen_random_uuid(), usd.id, quote.id, 'USD/' || quote.symbol, 'ACTIVE', 'TWELVEDATA',
       'USD/' || quote.symbol, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM assets usd
JOIN assets quote ON quote.asset_type = 'FIAT' AND quote.symbol IN ('JPY', 'CNY', 'AED')
WHERE usd.symbol = 'USD' AND usd.asset_type = 'FIAT'
  AND NOT EXISTS (
      SELECT 1 FROM asset_pairs p
      WHERE (p.base_asset_id = usd.id AND p.quote_asset_id = quote.id)
         OR (p.base_asset_id = quote.id AND p.quote_asset_id = usd.id)
  );

-- Map any of the seven currencies' USD pairs that still have no provider mapping (empty only; never overwrite).
UPDATE asset_pairs p
SET provider_code = 'TWELVEDATA',
    provider_pair_code = b.symbol || '/' || q.symbol,
    updated_at = CURRENT_TIMESTAMP
FROM assets b, assets q
WHERE b.id = p.base_asset_id AND q.id = p.quote_asset_id
  AND b.asset_type = 'FIAT' AND q.asset_type = 'FIAT'
  AND b.symbol IN ('USD', 'EUR', 'GBP', 'INR', 'JPY', 'CNY', 'AED')
  AND q.symbol IN ('USD', 'EUR', 'GBP', 'INR', 'JPY', 'CNY', 'AED')
  AND (b.symbol = 'USD' OR q.symbol = 'USD')
  AND p.provider_code IS NULL AND p.provider_pair_code IS NULL;
