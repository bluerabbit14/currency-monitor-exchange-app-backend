INSERT INTO assets (
    id,
    symbol,
    name,
    asset_type,
    currency_code,
    decimal_scale,
    status,
    provider,
    external_id,
    created_at,
    updated_at
) VALUES
    (gen_random_uuid(), 'USD', 'US Dollar', 'FIAT', 'USD', 2, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'INR', 'Indian Rupee', 'FIAT', 'INR', 2, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'EUR', 'Euro', 'FIAT', 'EUR', 2, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'GBP', 'British Pound', 'FIAT', 'GBP', 2, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'JPY', 'Japanese Yen', 'FIAT', 'JPY', 0, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'BTC', 'Bitcoin', 'CRYPTO', NULL, 8, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ETH', 'Ethereum', 'CRYPTO', NULL, 8, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SOL', 'Solana', 'CRYPTO', NULL, 8, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'BNB', 'BNB', 'CRYPTO', NULL, 8, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'GOLD', 'Gold', 'COMMODITY', NULL, 2, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SILVER', 'Silver', 'COMMODITY', NULL, 2, 'ACTIVE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
