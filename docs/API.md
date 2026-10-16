# Currency Monitor – API Documentation

Base URL (local): `http://localhost:8080`
All routes are prefixed with `/api` (versioned APIs use `/api/v1`).
Interactive docs: `/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`

## Contents

1. [Conventions](#conventions)
2. [Endpoint summary](#endpoint-summary)
3. [Health](#1-health)
4. [Auth](#2-auth)
5. [Users](#3-users)
6. [Assets](#4-assets)
7. [Rates](#5-rates)
8. [Favorites](#6-favorites)
9. [Price Alerts](#7-price-alerts)
10. [Notifications](#8-notifications)
11. [Notification Preferences](#9-notification-preferences)
12. [Devices](#10-devices)
13. [Enums](#enums)

---

## Conventions

### Authentication

Protected endpoints require a Bearer access token obtained from `POST /api/v1/auth/otp/verify` (or `/auth/refresh`).

```
Authorization: Bearer <accessToken>
```

- Access token TTL: 900 s (15 min). Refresh token TTL: 30 days.
- A missing, malformed, expired or invalid token returns `401 Unauthorized`.

### Common headers

| Header | When | Value |
|---|---|---|
| `Content-Type` | Requests with a body | `application/json` |
| `Accept` | Optional | `application/json` |
| `Authorization` | Protected endpoints | `Bearer <accessToken>` |

### Pagination (paginated list endpoints)

| Query param | Type | Default | Notes |
|---|---|---|---|
| `page` | int | `0` | Zero-based, non-negative |
| `size` | int | per endpoint | Positive, max `100` |
| `sort` | string | per endpoint | `field,asc\|desc`. Unknown fields → `400` |

Paginated response (Spring `Page<T>`):

```json
{
  "content": [ /* items */ ],
  "pageable": { "pageNumber": 0, "pageSize": 20 },
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false,
  "numberOfElements": 20,
  "empty": false
}
```

### Error format (RFC 7807 `application/problem+json`)

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "errors": [ { "field": "targetRate", "message": "targetRate must be positive" } ]
}
```

- `errors` is present only for validation failures.
- `code` is present on some rate errors: `UNSUPPORTED_PAIR`, `RATE_NOT_AVAILABLE`, `INVALID_RATE_HISTORY_RANGE`.

| Status | Meaning |
|---|---|
| 400 | Validation failed, malformed body, bad UUID / enum / timestamp, bad page/size/sort |
| 401 | Missing/invalid access token, bad OTP, invalid refresh token |
| 404 | Resource not found (or not owned by caller) |
| 501 | Phone OTP not supported |
| 503 | Email delivery or database unavailable |
| 500 | Unexpected error |

---

## Endpoint summary

| # | Category | Method | Route | Auth |
|---|---|---|---|---|
| 1 | Health | GET | `/api/health` | No |
| 2 | Auth | POST | `/api/v1/auth/otp/send` | No |
| 3 | Auth | POST | `/api/v1/auth/otp/verify` | No |
| 4 | Auth | POST | `/api/v1/auth/refresh` | No |
| 5 | Auth | POST | `/api/v1/auth/logout` | No |
| 6 | Users | GET | `/api/v1/users/me` | Yes |
| 7 | Users | PATCH | `/api/v1/users/me` | Yes |
| 8 | Account deletion | POST | `/api/v1/users/me/deletion/otp` (send identity OTP), `/api/v1/users/me/deletion` (request, 30-day grace), `/api/v1/auth/deletion/cancel` (cancel + sign in; no auth header) | Yes / Yes / No |
| 9 | Assets | GET | `/api/v1/assets` | No |
| 10 | Assets | GET | `/api/v1/assets/{id}` | No |
| 11 | Rates | GET | `/api/v1/rates` | No |
| 12 | Rates | GET | `/api/v1/rates/{base}/{quote}` | No |
| 13 | Rates | GET | `/api/v1/rates/{base}/{quote}/history` | No |
| 14 | Favorites | POST | `/api/v1/users/me/favorites/{assetId}` | Yes |
| 15 | Favorites | GET | `/api/v1/users/me/favorites` | Yes |
| 16 | Favorites | DELETE | `/api/v1/users/me/favorites/{assetId}` | Yes |
| 17 | Price Alerts | POST | `/api/v1/users/me/alerts` | Yes |
| 18 | Price Alerts | GET | `/api/v1/users/me/alerts` | Yes |
| 19 | Price Alerts | GET | `/api/v1/users/me/alerts/{alertId}` | Yes |
| 20 | Price Alerts | PATCH | `/api/v1/users/me/alerts/{alertId}` | Yes |
| 21 | Price Alerts | DELETE | `/api/v1/users/me/alerts/{alertId}` | Yes |
| 22 | Notifications | GET | `/api/v1/users/me/notifications` | Yes |
| 23 | Notifications | GET | `/api/v1/users/me/notifications/{notificationId}` | Yes |
| 24 | Notifications | PATCH | `/api/v1/users/me/notifications/read-all` | Yes |
| 25 | Notifications | PATCH | `/api/v1/users/me/notifications/{notificationId}/read` | Yes |
| 26 | Notifications | DELETE | `/api/v1/users/me/notifications/{notificationId}` | Yes |
| 27 | Notification Preferences | GET | `/api/v1/users/me/notification-preferences` | Yes |
| 28 | Notification Preferences | PATCH | `/api/v1/users/me/notification-preferences` | Yes |
| 29 | Devices | POST | `/api/v1/users/me/devices` | Yes |
| 30 | Devices | GET | `/api/v1/users/me/devices` | Yes |
| 31 | Devices | PATCH | `/api/v1/users/me/devices/{deviceId}` | Yes |
| 32 | Devices | DELETE | `/api/v1/users/me/devices/{deviceId}` | Yes |

---

## 1. Health

Controller: `HealthController`

### GET `/api/health`

Checks that the app and database are reachable.

- **Auth:** none
- **Headers:** none
- **Response 200:**
```json
{ "status": "UP", "database": "UP" }
```
- **Errors:** `503` database unavailable

---

## 2. Auth

Controller: `AuthController` · Base: `/api/v1/auth` · No authentication required.
Headers: `Content-Type: application/json`

### POST `/api/v1/auth/otp/send` — `sendOtp`

Sends a 6-digit OTP (valid 300 s, max 5 attempts) to an email address. Always returns the same message to avoid account enumeration.

**Request – `OtpSendRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `contact` | string | yes | Email address. Phone (`+<E.164>`) is recognised but not supported yet (501) |

```json
{ "contact": "user@example.com" }
```

**Response 200 – `OtpSendResponse`**
```json
{ "message": "If an account exists for this contact, an OTP has been sent." }
```

**Errors:** `400` blank/invalid contact · `501` phone OTP not supported · `503` email delivery failed

### POST `/api/v1/auth/otp/verify` — `verifyOtp`

Verifies the OTP and returns tokens. Creates the user on first sign-in.

**Request – `OtpVerifyRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `contact` | string | yes | Same contact used in send |
| `otp` | string | yes | Exactly 6 digits (`^\d{6}$`) |

```json
{ "contact": "user@example.com", "otp": "123456" }
```

**Response 200 – `AuthTokenResponse`**

| Field | Type | Description |
|---|---|---|
| `accessToken` | string | JWT for the `Authorization` header |
| `refreshToken` | string | Opaque token for `/refresh` and `/logout` |
| `tokenType` | string | Always `Bearer` |
| `expiresInSeconds` | long | Access token lifetime (900) |

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "r_9f2c...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```

**Errors:** `400` validation · `401` invalid/expired OTP or attempts exceeded

### POST `/api/v1/auth/refresh` — `refreshToken`

Exchanges a refresh token for a new token pair.

**Request – `RefreshTokenRequest`**

| Field | Type | Required |
|---|---|---|
| `refreshToken` | string | yes |

```json
{ "refreshToken": "r_9f2c..." }
```

**Response 200:** `AuthTokenResponse` (see above)
**Errors:** `400` validation · `401` refresh token invalid, revoked or expired

### POST `/api/v1/auth/logout` — `logout`

Revokes the refresh session.

**Request:** `RefreshTokenRequest` (see above)
**Response 204:** no body
**Errors:** `400` validation

---

## 3. Users

Controller: `UserController` · Base: `/api/v1/users`
**Auth required** · Headers: `Authorization: Bearer <accessToken>`; `Content-Type: application/json` on PATCH.

### GET `/api/v1/users/me` — `getProfile`

**Request:** none
**Response 200 – `UserProfileResponse`**

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `email` | string | nullable |
| `phoneNumber` | string | nullable |
| `displayName` | string | nullable |
| `profileImageUrl` | string | nullable |
| `createdAt` | ISO-8601 instant | |
| `updatedAt` | ISO-8601 instant | |

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "email": "user@example.com",
  "phoneNumber": null,
  "displayName": "Asif",
  "profileImageUrl": "https://example.com/me.png",
  "createdAt": "2026-10-12T04:00:00Z",
  "updatedAt": "2026-10-12T04:00:00Z"
}
```

**Errors:** `401`

### PATCH `/api/v1/users/me` — `updateProfile`

**Request – `UpdateUserProfileRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `displayName` | string | no | max 100 chars |
| `profileImageUrl` | string | no | max 2048 chars; must be a valid http(s) URL |

```json
{ "displayName": "Asif", "profileImageUrl": "https://example.com/me.png" }
```

**Response 200:** `UserProfileResponse`
**Errors:** `400` validation · `401`

### DELETE `/api/v1/users/me` — `deleteProfile`

Deletes the signed-in user's account.

**Response 204:** no body
**Errors:** `401`

---

## 4. Assets

Controller: `AssetController` · Base: `/api/v1/assets` · **Public** (no auth). Read-only catalog of active assets.

### GET `/api/v1/assets` — `list`

**Query params**

| Param | Type | Required | Description |
|---|---|---|---|
| `type` | `FIAT` \| `CRYPTO` \| `COMMODITY` | no | Filter by asset type |
| `search` | string | no | Case-insensitive match on symbol or name |
| `page`, `size`, `sort` | – | no | Default `size=20`, `sort=name,asc` |

**Response 200:** `Page<AssetResponse>`

**`AssetResponse`**

| Field | Type |
|---|---|
| `id` | UUID |
| `symbol` | string |
| `name` | string |
| `type` | `FIAT` \| `CRYPTO` \| `COMMODITY` |
| `status` | `ACTIVE` \| `INACTIVE` |

```json
{
  "content": [
    { "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "symbol": "BTC", "name": "Bitcoin", "type": "CRYPTO", "status": "ACTIVE" }
  ],
  "totalElements": 1, "totalPages": 1, "number": 0, "size": 20
}
```

**Errors:** `400` invalid `type`, page, size or sort

### GET `/api/v1/assets/{id}` — `get`

| Path param | Type | Description |
|---|---|---|
| `id` | UUID | Asset id |

**Response 200:** `AssetResponse`
**Errors:** `400` id not a UUID · `404` asset missing or inactive

---

## 5. Rates

Controller: `RateController` · Base: `/api/v1/rates` · **Public** (no auth). Serves cached current and historical rates.

### GET `/api/v1/rates` — `list`

**Query params**

| Param | Type | Required | Description |
|---|---|---|---|
| `type` | `FIAT` \| `CRYPTO` \| `COMMODITY` | no | Filter by base asset type |
| `base` | string | no | Base symbol, e.g. `BTC` |
| `quote` | string | no | Quote symbol, e.g. `USD` |
| `page`, `size`, `sort` | – | no | Default `size=20`, `sort=observedAt,desc` |

**Response 200:** `Page<RateResponse>`

**`RateResponse`**

| Field | Type | Description |
|---|---|---|
| `base` | string | Base symbol |
| `quote` | string | Quote symbol |
| `rate` | decimal | Units of quote per 1 base |
| `observedAt` | instant | When the provider observed the rate |
| `fetchedAt` | instant | When we fetched it |
| `stale` | boolean | `true` if `observedAt` is older than 15 min |
| `providerCode` | string | Rate provider |

```json
{
  "base": "BTC", "quote": "USD", "rate": 67250.12,
  "observedAt": "2026-10-12T04:00:00Z", "fetchedAt": "2026-10-12T04:00:05Z",
  "stale": false, "providerCode": "coingecko"
}
```

**Errors:** `400` invalid `type`, blank symbol, page/size/sort

### GET `/api/v1/rates/{base}/{quote}` — `get`

| Path param | Type | Example |
|---|---|---|
| `base` | string | `USD` |
| `quote` | string | `INR` |

**Response 200:** `RateResponse`
**Errors:** `404` with `code` = `UNSUPPORTED_PAIR` (pair not configured) or `RATE_NOT_AVAILABLE` (no cached rate yet)

### GET `/api/v1/rates/{base}/{quote}/history` — `history`

| Param | Where | Type | Required | Description |
|---|---|---|---|---|
| `base`, `quote` | path | string | yes | Pair symbols |
| `from` | query | ISO-8601 instant | no | Inclusive start |
| `to` | query | ISO-8601 instant | no | Exclusive end |
| `page`, `size`, `sort` | query | – | no | Default `size=100`, `sort=observedAt,id desc` |

**Response 200:** `Page<RateHistoryResponse>`

**`RateHistoryResponse`**

| Field | Type |
|---|---|
| `rate` | decimal |
| `observedAt` | instant |
| `fetchedAt` | instant |
| `providerCode` | string |

**Errors:** `400` bad timestamp, or `code` = `INVALID_RATE_HISTORY_RANGE` (`from` not earlier than `to`) · `404` `UNSUPPORTED_PAIR`

---

## 6. Favorites

Controller: `FavoriteController` · Base: `/api/v1/users/me/favorites`
**Auth required** · Headers: `Authorization: Bearer <accessToken>`

**`FavoriteAssetResponse`**

| Field | Type | Description |
|---|---|---|
| `assetId` | UUID | |
| `symbol` | string | |
| `name` | string | |
| `type` | `FIAT` \| `CRYPTO` \| `COMMODITY` | |
| `status` | `ACTIVE` \| `INACTIVE` | |
| `createdAt` | instant | When favorited |

### POST `/api/v1/users/me/favorites/{assetId}` — `add`

Adds an asset to favorites (idempotent).

| Path param | Type |
|---|---|
| `assetId` | UUID |

**Request body:** none
**Response 200:** `FavoriteAssetResponse`
**Errors:** `400` assetId not a UUID · `401` · `404` asset not found / inactive

### GET `/api/v1/users/me/favorites` — `list`

**Response 200:** `FavoriteAssetResponse[]` (plain array, not paginated)
**Errors:** `401`

### DELETE `/api/v1/users/me/favorites/{assetId}` — `remove`

**Response 204:** no body (also returned if it was not a favorite)
**Errors:** `400` · `401`

---

## 7. Price Alerts

Controller: `PriceAlertController` · Base: `/api/v1/users/me/alerts`
**Auth required** · Headers: `Authorization: Bearer <accessToken>`; `Content-Type: application/json` on POST/PATCH.

**`PriceAlertResponse`**

| Field | Type | Description |
|---|---|---|
| `id` | UUID | |
| `base` | string | Base symbol |
| `quote` | string | Quote symbol |
| `condition` | `ABOVE` \| `BELOW` | |
| `targetRate` | decimal | |
| `status` | `ACTIVE` \| `TRIGGERED` \| `CANCELLED` | |
| `createdAt` | instant | |
| `updatedAt` | instant | |
| `triggeredAt` | instant | nullable |

### POST `/api/v1/users/me/alerts` — `create`

Creates an `ACTIVE` alert for an existing active pair. Does not evaluate/trigger it immediately.

**Request – `CreatePriceAlertRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `base` | string | yes | not blank |
| `quote` | string | yes | not blank |
| `condition` | `ABOVE` \| `BELOW` | yes | |
| `targetRate` | decimal | yes | > 0 |

```json
{ "base": "USD", "quote": "INR", "condition": "ABOVE", "targetRate": 90.00 }
```

**Response 201:** `PriceAlertResponse`
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "base": "USD", "quote": "INR", "condition": "ABOVE", "targetRate": 90.00,
  "status": "ACTIVE",
  "createdAt": "2026-10-12T04:00:00Z", "updatedAt": "2026-10-12T04:00:00Z",
  "triggeredAt": null
}
```
**Errors:** `400` validation · `401` · `404` pair not configured or inactive

### GET `/api/v1/users/me/alerts` — `list`

| Query param | Notes |
|---|---|
| `page`, `size`, `sort` | Default `size=20`, `sort=createdAt,desc` |

**Response 200:** `Page<PriceAlertResponse>`
**Errors:** `400` · `401`

### GET `/api/v1/users/me/alerts/{alertId}` — `get`

| Path param | Type |
|---|---|
| `alertId` | UUID |

**Response 200:** `PriceAlertResponse`
**Errors:** `400` · `401` · `404`

### PATCH `/api/v1/users/me/alerts/{alertId}` — `update`

Partial update; omitted fields are unchanged.

**Request – `UpdatePriceAlertRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `condition` | `ABOVE` \| `BELOW` | no | |
| `targetRate` | decimal | no | > 0 when provided |

```json
{ "condition": "BELOW", "targetRate": 89.50 }
```

**Response 200:** `PriceAlertResponse`
**Errors:** `400` · `401` · `404`

### DELETE `/api/v1/users/me/alerts/{alertId}` — `delete`

**Response 204:** no body
**Errors:** `400` · `401` · `404`

---

## 8. Notifications

Controller: `NotificationController` · Base: `/api/v1/users/me/notifications`
**Auth required** · Headers: `Authorization: Bearer <accessToken>`

**`NotificationResponse`**

| Field | Type | Description |
|---|---|---|
| `id` | UUID | |
| `type` | `PRICE_ALERT` | |
| `title` | string | e.g. "Price alert triggered" |
| `message` | string | User-facing body |
| `referenceId` | UUID | nullable; linked entity (e.g. alert id) |
| `isRead` | boolean | |
| `createdAt` | instant | |
| `readAt` | instant | nullable |

### GET `/api/v1/users/me/notifications` — `list`

| Query param | Type | Default | Description |
|---|---|---|---|
| `unreadOnly` | boolean | `false` | Only unread when `true` |
| `page`, `size`, `sort` | – | `size=20`, `createdAt,desc` | |

**Response 200:** `Page<NotificationResponse>`
**Errors:** `400` · `401`

### GET `/api/v1/users/me/notifications/{notificationId}` — `get`

**Response 200:** `NotificationResponse`
**Errors:** `400` · `401` · `404`

### PATCH `/api/v1/users/me/notifications/read-all` — `markAllRead`

**Request body:** none
**Response 200 – `MarkAllReadResponse`**
```json
{ "updatedCount": 3 }
```
**Errors:** `401`

### PATCH `/api/v1/users/me/notifications/{notificationId}/read` — `markRead`

**Request body:** none
**Response 200:** `NotificationResponse`
**Errors:** `400` · `401` · `404`

### DELETE `/api/v1/users/me/notifications/{notificationId}` — `delete`

**Response 204:** no body
**Errors:** `400` · `401` · `404`

---

## 9. Notification Preferences

Controller: `NotificationPreferenceController` · Base: `/api/v1/users/me/notification-preferences`
**Auth required** · Headers: `Authorization: Bearer <accessToken>`; `Content-Type: application/json` on PATCH.

**`NotificationPreferenceResponse`**

| Field | Type |
|---|---|
| `pushEnabled` | boolean |
| `inAppEnabled` | boolean |
| `emailEnabled` | boolean |
| `createdAt` | instant |
| `updatedAt` | instant |

### GET `/api/v1/users/me/notification-preferences` — `getPreferences`

Returns preferences, creating defaults on first call.

**Response 200:** `NotificationPreferenceResponse`
```json
{
  "pushEnabled": true, "inAppEnabled": true, "emailEnabled": false,
  "createdAt": "2026-10-12T04:00:00Z", "updatedAt": "2026-10-12T04:00:00Z"
}
```
**Errors:** `401`

### PATCH `/api/v1/users/me/notification-preferences` — `updatePreferences`

**Request – `UpdateNotificationPreferenceRequest`** (at least one field required)

| Field | Type | Required |
|---|---|---|
| `pushEnabled` | boolean | no |
| `inAppEnabled` | boolean | no |
| `emailEnabled` | boolean | no |

```json
{ "pushEnabled": true, "emailEnabled": false }
```

**Response 200:** `NotificationPreferenceResponse`
**Errors:** `400` empty update (no fields supplied) · `401`

---

## 10. Devices

Controller: `DeviceController` · Base: `/api/v1/users/me/devices`
**Auth required** · Headers: `Authorization: Bearer <accessToken>`; `Content-Type: application/json` on POST/PATCH.

**`DeviceResponse`** (FCM token is never returned)

| Field | Type |
|---|---|
| `id` | UUID |
| `platform` | `ANDROID` |
| `deviceName` | string (nullable) |
| `isActive` | boolean |
| `createdAt` | instant |
| `updatedAt` | instant |
| `lastSeenAt` | instant |

### POST `/api/v1/users/me/devices` — `register`

Registers a device or refreshes an existing FCM token.

**Request – `RegisterDeviceRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `fcmToken` | string | yes | not blank, max 4096 |
| `platform` | `ANDROID` | yes | only `ANDROID` is supported |
| `deviceName` | string | no | max 120 |

```json
{ "fcmToken": "example-fcm-registration-token", "platform": "ANDROID", "deviceName": "My Android Phone" }
```

**Response 200:** `DeviceResponse`
**Errors:** `400` validation · `401`

### GET `/api/v1/users/me/devices` — `list`

**Response 200:** `DeviceResponse[]` (plain array)
**Errors:** `401`

### PATCH `/api/v1/users/me/devices/{deviceId}` — `update`

**Request – `UpdateDeviceRequest`**

| Field | Type | Required | Rules |
|---|---|---|---|
| `deviceName` | string | no | max 120; blank clears the name |

```json
{ "deviceName": "Kitchen tablet" }
```

**Response 200:** `DeviceResponse`
**Errors:** `400` · `401` · `404`

### DELETE `/api/v1/users/me/devices/{deviceId}` — `unregister`

Deactivates the device (idempotent).

**Response 204:** no body
**Errors:** `400` · `401` · `404`

---

## Enums

| Enum | Values |
|---|---|
| `AssetType` | `FIAT`, `CRYPTO`, `COMMODITY` |
| `AssetStatus` | `ACTIVE`, `INACTIVE` |
| `AlertCondition` | `ABOVE`, `BELOW` |
| `AlertStatus` | `ACTIVE`, `TRIGGERED`, `CANCELLED` |
| `NotificationType` | `PRICE_ALERT` |
| `DevicePlatform` | `ANDROID` (accepted on register); `IOS`, `WEB` exist in the enum |
