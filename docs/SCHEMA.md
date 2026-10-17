# Database schema

PostgreSQL, managed by Flyway (`src/main/resources/db/migration`). Hibernate runs with `ddl-auto=validate`,
so every mapped entity must match the schema. Tables without an entity yet are marked *(no entity yet)*.

## Domain map

```
users ─┬─ user_auth_providers     (GOOGLE | APPLE | EMAIL | PHONE)
       ├─ refresh_sessions        (hashed tokens, revocable, ip/user_agent)
       ├─ system_logs             (append-only event trail: email / push / in-app / sms; user_id SET NULL)
       ├─ user_devices            (push tokens)
       ├─ notification_preferences
       ├─ user_favorites ──► assets
       ├─ price_alerts ──► asset_pairs ; alert_events ──► notifications
       ├─ subscriptions ──► plans ─► plan_entitlements      (no entity yet)
       └─ base_asset_id ──► assets   (main comparison currency)

assets (FIAT | CRYPTO | COMMODITY)
asset_pairs (base, quote) ─┬─ exchange_rates   latest rate, 1 row per pair
                           ├─ rate_history     raw observations
                           └─ rate_candles     OHLC rollups for charts   (no entity yet)
```

## Decisions

| Topic | Decision |
|---|---|
| Saved assets | `user_favorites` only. Watchlist tables/entities removed in V12. |
| Latest rate | `exchange_rates`: `observed_at` (provider time), `fetched_at` (our fetch), `updated_at` (row write, set by `@PrePersist/@PreUpdate`). |
| Charts | Never scan `rate_history` for ranges beyond 1D. Read `rate_candles` by `(pair_id, bucket_interval, bucket_start)`. |
| Main currency | `users.base_asset_id` (nullable, app default USD). Must reference a FIAT asset (enforce in service). |
| Alerts | `target_price` is always the trigger level. PERCENT alerts store `reference_price` + `percent_threshold`, and `target_price` is derived at creation. Evaluator compares only `target_price`. |
| Plans | Entitlements are rows (`feature_key`, `enabled`, `limit_value`, NULL = unlimited). Limits change by data update, no deploy. |
| Webhooks | `payment_events (provider, provider_event_id)` is unique, which makes store/Stripe webhooks idempotent. |
| Providers | Per-asset `assets.provider/external_id` and per-pair `asset_pairs.provider_code/provider_pair_code`. |

## Provider mapping (seeded in V12)

| Category | Provider | `provider_code` | `provider_pair_code` | Example |
|---|---|---|---|---|
| FIAT/FIAT | Twelve Data | `TWELVEDATA` | `BASE/QUOTE` | `USD/INR` |
| CRYPTO/FIAT | CoinGecko | `COINGECKO` | `<coingecko id>:<vs currency>` | `bitcoin:usd` |
| COMMODITY | Gold API | `GOLDAPI` | provider symbol | `XAU` |

## Entitlement keys (seeded)

| Key | FREE | PRO |
|---|---|---|
| `max_active_alerts` | 5 | 100 |
| `max_favorites` | 10 | unlimited |
| `max_compare_assets` | 2 | 10 |
| `chart_max_range_days` | 30 | unlimited |
| `min_refresh_seconds` | 60 | 5 |
| `percent_alerts` | off | on |
| `repeating_alerts` | off | on |

These numbers are placeholders. Decide real limits before launch.

## Migration history

| Version | Purpose |
|---|---|
| V1 | Initial domain (users, assets, pairs, rates, alerts, notifications, devices) |
| V2 | `refresh_sessions` |
| V3-V4 | Asset catalog (FIAT/CRYPTO/COMMODITY, status, provider) and seed |
| V5-V6 | Rate module alignment, supported pairs seed |
| V7 | `user_favorites` |
| V8 | alerts index |
| V9 | `user_devices.is_active` |
| V10-V11 | notification read state, in-app preference |
| **V12** | Foundation: drop watchlists, `exchange_rates.updated_at`, user settings, `rate_candles`, alert modes, notification to alert_event link, session/OTP audit columns, plans/subscriptions, provider mapping |

## Known follow-ups (not in V12)

- `alert_events.notification_status` duplicates `notifications.status`. Remove once the outbox worker exists.
- `assets.symbol` is globally unique. Crypto tickers can collide across coins, so move to `(asset_type, symbol)` or a slug before adding many coins.
- `user_devices.device_token` is globally unique. `DeviceService` must upsert and re-parent a token to the new user on login.
- `AlertStatus` and `AlertCondition` enums in code do not yet include `PAUSED`/`EXPIRED`/alert type/recurrence added to the DB.
- `rate_history`: partition by month and add a retention job once ingestion is live.
- OTPs and pending registrations live in Redis (key TTL = expiry), so no purge job is needed. Add one for expired `refresh_sessions`, and a retention job for `system_logs`.
