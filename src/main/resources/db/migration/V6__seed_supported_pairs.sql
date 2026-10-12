-- Configured trading pairs only. No current or historical rates are seeded here.
INSERT INTO asset_pairs (
    id,
    base_asset_id,
    quote_asset_id,
    symbol,
    status,
    provider_code,
    provider_pair_code,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    base.id,
    quote.id,
    pair.symbol,
    'ACTIVE',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (
    VALUES
        ('USD', 'INR', 'USD/INR'),
        ('EUR', 'USD', 'EUR/USD'),
        ('GBP', 'USD', 'GBP/USD'),
        ('BTC', 'USD', 'BTC/USD'),
        ('ETH', 'USD', 'ETH/USD'),
        ('SOL', 'USD', 'SOL/USD'),
        ('GOLD', 'USD', 'GOLD/USD'),
        ('SILVER', 'USD', 'SILVER/USD')
) AS pair(base_symbol, quote_symbol, symbol)
JOIN assets base ON base.symbol = pair.base_symbol
JOIN assets quote ON quote.symbol = pair.quote_symbol;
