# Integration & roadmap plan

Status legend: DONE = in code, SCHEMA = schema ready only, TODO = nothing yet.

## 1. Rate ingestion

Existing seams: `RateCollectionService.collect()` (placeholder), `RateCollectionScheduler` (placeholder),
`RatePersistenceService` (upserts `exchange_rates`, appends `rate_history`, dedup by provider observation id).
Ingestion stays independent of HTTP controllers.

### Design

```
Scheduler (ShedLock) ─► RateCollectionService
        for each ProviderClient:  select active pairs where provider_code = client.code()
                                  batch fetch ─► List<RateObservation>
        ─► RatePersistenceService.record(obs)        (history + latest, idempotent)
        ─► CandleAggregator (updates rate_candles)
        ─► AlertEvaluator.evaluate(pairIds)          (section 3)
```

```java
interface RateProvider {
    String code();                                   // TWELVEDATA | COINGECKO | ...
    List<RateObservation> fetch(List<AssetPair> pairs);
}
```

Each provider is its own `@Component` with its own `RestClient`, timeout, retry and circuit breaker, so one
provider failing never blocks the others.

### Twelve Data (fiat)

- Auth: `apikey` as a query parameter or `Authorization: apikey <key>` header. Config `app.rates.twelvedata.api-key`
  from env `TWELVEDATA_API_KEY`. Never commit the key. `.env` is already git-ignored.
- Endpoint: `GET /price?symbol=USD/INR,EUR/USD&apikey=...` batch (use the batch form to save credits).
  Use `/time_series` only for backfilling history.
- Mapping: `asset_pairs.provider_pair_code` (`USD/INR`).
- **Limits:** free tier has a small per-minute and per-day credit budget, and batch requests cost one credit per
  symbol. Confirm your plan's numbers and size the polling interval from
  `active_pairs x polls_per_day <= daily_credits`. Fiat moves slowly, so 1-5 min is plenty.
- Response: `price` as string, so parse to `BigDecimal` and never `double`. `observed_at = fetched_at` when the
  provider supplies no timestamp.

### CoinGecko (crypto)

- Auth: demo or pro key header (`x-cg-demo-api-key` / `x-cg-pro-api-key`). Config `COINGECKO_API_KEY`.
- Endpoint: `GET /simple/price?ids=bitcoin,ethereum&vs_currencies=usd&include_last_updated_at=true`
  (one call for all coins, so it is cheap).
- Mapping: `assets.external_id` (`bitcoin`) + quote currency from `provider_pair_code` (`bitcoin:usd`).
- `last_updated_at` maps to `observed_at`. Skip observations not newer than the stored one (already handled
  in `upsertCurrentRate`).
- Free tier rate limits are modest, so poll 30-60 s and handle HTTP 429 with `Retry-After`.

### Commodities (undecided)

Candidates to evaluate: Twelve Data itself (supports XAU/USD, XAG/USD on paid plans, which would avoid a
third vendor), a metals-specific API, or a commodities API. Decision criteria: licence allows redistribution
in a consumer app, update frequency, symbol coverage (oil, gas, etc.), price. Until then the commodity pairs (XAU, XAG, XPT, XPD, HG against USD) have
`provider_code = NULL` and the collector skips them. API will report them as "no rate available".

### Licensing check (do before launch)

Both vendors' free tiers restrict commercial/display use. Verify the plan you ship on permits showing data in
a public app, and add the required attribution (CoinGecko requires it on free plans).

### Cross rates (compare and "main currency")

Do not store every `X/<user currency>` pair. Keep USD-quoted pairs and compute at read time:

```
A/B = (A/USD) / (B/USD)
```

`RateQueryService` gets a `CrossRateResolver`. A user's base currency (`users.base_asset_id`) then works for any
asset with zero extra provider calls, and `compare` is the same function over a list.

## 2. Charts and history

- `rate_candles` buckets: `1M`, `5M`, `1H`, `1D`. `CandleAggregator` upserts the open bucket on each observation
  (`INSERT ... ON CONFLICT (pair_id, bucket_interval, bucket_start) DO UPDATE` with min/max/close).
- Range to bucket: 1D to 5M, 1W to 1H, 1M to 1H, 6M to 1D, 1Y/ALL to 1D.
- API: `GET /api/v1/assets/{symbol}/chart?range=1D|1W|1M|6M|1Y|ALL&quote=INR` returning
  `{ points:[{t,o,h,l,c}], changeAbs, changePercent }`. Quote defaults to the user's base currency.
- Enforce `chart_max_range_days` entitlement (FREE: 30 days) so 6M and 1Y are PRO.
- Backfill on first deploy from Twelve Data `/time_series` and CoinGecko `/coins/{id}/market_chart`.
- 24h change: computed from candles (or cached on `exchange_rates` later if reads get hot).

## 3. Alerts

Evaluate after each ingestion batch, not on a separate poll.

1. Load `ACTIVE` alerts for the changed `pair_id`s (partial index `price_alerts_active_pair_idx`).
2. For each: ABOVE fires when `rate >= target_price`, BELOW when `rate <= target_price`.
3. In **one transaction**: set `status=TRIGGERED` (or keep `ACTIVE` + `last_triggered_at` for `REPEATING`
   respecting `cooldown_seconds`), insert `alert_events`, insert `notifications (status=PENDING, alert_event_id)`.
4. A separate `NotificationDispatcher` polls `notifications_pending_idx`, sends push (FCM) / email (Resend),
   then marks `SENT` or `FAILED`. No external I/O inside the evaluation transaction.
5. Expiry job: `ACTIVE` alerts with `expires_at < now()` go to `EXPIRED`.

PERCENT alerts: at creation compute `target_price = reference_price * (1 + p)` for rise or `(1 - p)` for fall.
Gate with `percent_alerts` and `repeating_alerts` entitlements.

## 4. Auth

| Item | Status | Plan |
|---|---|---|
| Email OTP | DONE | Revert the plaintext-OTP debug change in `OtpService` (login is currently broken by it). |
| Spring Security filter | TODO | Replace per-controller `tokenService.requireUserId`; default-deny; resource-server style JWT filter. |
| Rate limiting | TODO | Per email + per IP on `/otp/send` and `/otp/verify` (Bucket4j, or Redis when scaling). |
| Google / Apple OIDC | TODO | `POST /auth/oidc/{provider}` takes an ID token, verify via provider JWKS (`iss`, `aud`, `exp`, `nonce`), upsert `user_auth_providers`. Link to existing user only if the provider email is verified. Apple only returns email/name on first login, so persist it then. |
| Phone OTP | TODO | Needs an SMS provider (cost + regional sender rules), then flip `PhoneOtpNotSupportedException`. |
| Session audit | SCHEMA | Fill `ip_address`/`user_agent` on `refresh_sessions` and `system_logs`. |

## 5. Monetization

1. `EntitlementService.require(userId, featureKey)` and `limit(userId, featureKey)`. Resolve active
   subscription to plan, otherwise the default FREE plan. Cache per user for a short TTL.
2. Call sites: alert create (`max_active_alerts`, `percent_alerts`, `repeating_alerts`), favorite add
   (`max_favorites`), chart (`chart_max_range_days`), compare (`max_compare_assets`).
3. Return `403` with ProblemDetail `type=.../entitlement-required` and `feature` so clients can show an upsell.
4. Purchases: Apple App Store Server Notifications v2 and Google Play RTDN webhooks write `payment_events`
   (idempotent by provider event id), then update `subscriptions`. Stripe for web if needed.
5. Downgrade behaviour: existing alerts/favorites beyond the new limit are kept but cannot be re-armed or added to.
6. Expose `GET /api/v1/users/me/entitlements` for the client.

## 6. Cross-cutting

- Push delivery: FCM via `user_devices`; deactivate tokens on `UNREGISTERED`.
- Live prices to clients: start with polling `GET /rates` honouring `min_refresh_seconds`; add SSE later.
- Cache: Caffeine for latest rates and entitlements now, Redis when running multiple instances.
- Multi-instance safety: ShedLock on the scheduler.
- Ops: Spring Actuator, structured logs, purge jobs for OTP/refresh sessions, `rate_history` partitioning.
- Tests: Testcontainers IT for migrations V1-V12 + `validate`, provider clients with WireMock,
  alert evaluator unit tests (boundary equality, cooldown, expiry).

## 7. Suggested order

1. Revert plaintext OTP, Spring Security filter, OTP rate limiting.
2. Settings: `PATCH /users/me` accepts base currency, locale, timezone (validate FIAT).
3. Twelve Data + CoinGecko clients, scheduler, persistence, candles, backfill.
4. Chart + convert/compare endpoints (USD-pivot cross rates).
5. Alert evaluator, outbox dispatcher, FCM.
6. Entitlements, then store webhooks.
7. OIDC (Google, Apple), then phone OTP.
8. Commodity provider decision, populate `provider_code` for the commodity pairs (XAU, XAG, XPT, XPD, HG).
