-- Copper (HG) is no longer a supported commodity: its price provider is undecided and the Gold API does not
-- document the unit of its HG price. V18 created the asset and its HG/USD pair; this removes them.
-- Safe by construction: anything that still references copper (rates, history, candles, alerts, favorites,
-- a user's display currency) is kept, and the asset or pair is only deactivated instead of deleted.

-- 1. The HG/USD pair: delete when nothing depends on it, otherwise deactivate.
DELETE FROM asset_pairs p
USING assets a
WHERE a.id = p.base_asset_id AND a.symbol = 'HG' AND a.asset_type = 'COMMODITY'
  AND NOT EXISTS (SELECT 1 FROM exchange_rates x WHERE x.pair_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM rate_history h WHERE h.pair_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM rate_candles c WHERE c.pair_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM price_alerts pa WHERE pa.pair_id = p.id);

UPDATE asset_pairs p
SET status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP
FROM assets a
WHERE a.id = p.base_asset_id AND a.symbol = 'HG' AND a.asset_type = 'COMMODITY'
  AND p.status <> 'INACTIVE';

-- 2. The HG asset: delete when nothing depends on it, otherwise deactivate.
DELETE FROM assets a
WHERE a.symbol = 'HG' AND a.asset_type = 'COMMODITY'
  AND NOT EXISTS (SELECT 1 FROM asset_pairs p WHERE p.base_asset_id = a.id OR p.quote_asset_id = a.id)
  AND NOT EXISTS (SELECT 1 FROM user_favorites f WHERE f.asset_id = a.id)
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.base_asset_id = a.id);

UPDATE assets
SET status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE symbol = 'HG' AND asset_type = 'COMMODITY' AND status <> 'INACTIVE';
