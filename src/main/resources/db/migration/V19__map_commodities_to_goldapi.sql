-- Gold API (api.gold-api.com) provides XAU, XAG, XPT and XPD prices in USD at GET /price/{symbol}.
-- HG (copper) is deliberately left unmapped: the provider documents the symbol but not the unit of its price.
-- Only empty mappings are filled; an existing mapping is never overwritten.

UPDATE assets
SET provider = 'GOLDAPI',
    external_id = symbol,
    updated_at = CURRENT_TIMESTAMP
WHERE asset_type = 'COMMODITY'
  AND symbol IN ('XAU', 'XAG', 'XPT', 'XPD')
  AND provider IS NULL
  AND external_id IS NULL;

UPDATE asset_pairs p
SET provider_code = 'GOLDAPI',
    provider_pair_code = base.external_id,
    updated_at = CURRENT_TIMESTAMP
FROM assets base, assets quote
WHERE base.id = p.base_asset_id AND quote.id = p.quote_asset_id
  AND quote.symbol = 'USD' AND quote.asset_type = 'FIAT'
  AND base.asset_type = 'COMMODITY' AND base.provider = 'GOLDAPI'
  AND base.symbol IN ('XAU', 'XAG', 'XPT', 'XPD')
  AND p.provider_code IS NULL AND p.provider_pair_code IS NULL;
