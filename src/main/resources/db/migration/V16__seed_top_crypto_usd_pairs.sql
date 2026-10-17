-- Canonical CRYPTO/USD pairs for the ten supported cryptocurrencies, collected from CoinGecko in USD.
-- Idempotent: existing assets and pairs (BTC, ETH, SOL, BNB) are reused, never duplicated.
-- Prices in other fiat currencies are derived at read time from these pairs and the stored FX pairs.

INSERT INTO assets (
    id, symbol, name, asset_type, currency_code, decimal_scale, status, provider, external_id, created_at, updated_at
)
SELECT gen_random_uuid(), v.symbol, v.name, 'CRYPTO', NULL, 8, 'ACTIVE', 'COINGECKO', v.external_id,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('USDT', 'Tether', 'tether'),
    ('USDC', 'USDC', 'usd-coin'),
    ('XRP', 'XRP', 'ripple'),
    ('DOGE', 'Dogecoin', 'dogecoin'),
    ('ADA', 'Cardano', 'cardano'),
    ('TRX', 'TRON', 'tron')
) AS v(symbol, name, external_id)
WHERE NOT EXISTS (SELECT 1 FROM assets a WHERE a.symbol = v.symbol);

-- Fill the provider mapping only where it is still empty; never overwrite an existing mapping.
UPDATE assets a
SET provider = 'COINGECKO',
    external_id = v.external_id,
    updated_at = CURRENT_TIMESTAMP
FROM (VALUES
    ('BTC', 'bitcoin'),
    ('ETH', 'ethereum'),
    ('USDT', 'tether'),
    ('BNB', 'binancecoin'),
    ('SOL', 'solana'),
    ('XRP', 'ripple'),
    ('USDC', 'usd-coin'),
    ('DOGE', 'dogecoin'),
    ('ADA', 'cardano'),
    ('TRX', 'tron')
) AS v(symbol, external_id)
WHERE a.symbol = v.symbol
  AND a.asset_type = 'CRYPTO'
  AND a.provider IS NULL
  AND a.external_id IS NULL;

INSERT INTO asset_pairs (
    id, base_asset_id, quote_asset_id, symbol, status, provider_code, provider_pair_code, created_at, updated_at
)
SELECT gen_random_uuid(), base.id, usd.id, base.symbol || '/USD', 'ACTIVE', 'COINGECKO',
       base.external_id || ':usd', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM assets base
JOIN assets usd ON usd.symbol = 'USD' AND usd.asset_type = 'FIAT'
WHERE base.asset_type = 'CRYPTO'
  AND base.provider = 'COINGECKO'
  AND base.symbol IN ('BTC', 'ETH', 'USDT', 'BNB', 'SOL', 'XRP', 'USDC', 'DOGE', 'ADA', 'TRX')
  AND NOT EXISTS (
      SELECT 1 FROM asset_pairs p WHERE p.base_asset_id = base.id AND p.quote_asset_id = usd.id
  );

-- An already-existing pair that never got its provider mapping.
UPDATE asset_pairs p
SET provider_code = 'COINGECKO',
    provider_pair_code = base.external_id || ':usd',
    updated_at = CURRENT_TIMESTAMP
FROM assets base, assets usd
WHERE base.id = p.base_asset_id AND usd.id = p.quote_asset_id
  AND usd.symbol = 'USD' AND usd.asset_type = 'FIAT'
  AND base.asset_type = 'CRYPTO' AND base.provider = 'COINGECKO' AND base.external_id IS NOT NULL
  AND base.symbol IN ('BTC', 'ETH', 'USDT', 'BNB', 'SOL', 'XRP', 'USDC', 'DOGE', 'ADA', 'TRX')
  AND p.provider_code IS NULL AND p.provider_pair_code IS NULL;
