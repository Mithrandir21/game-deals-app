# Pre-Release Hardening Audit

**Status:** IMPLEMENTED on `dev` (2026-06-22). All tiers landed in one change set. Verified on the Linux
dev box: `assembleDebug`, module host tests, app unit tests, and a real R8 `assembleRelease` all green;
Room schema regenerated to a single `1.json`. Still pending hardware: the iOS Keychain backend
(`:iosApp:compileKotlinIosSimulatorArm64` on macOS) and the on-device edge-to-edge / OAuth / notification
smoke tests after the targetSdk-36 bump.
**Audience:** maintainer, pre-1.0.
**Date:** 2026-06-21 (audit) · 2026-06-22 (implementation).

## Why this exists

The app has **no users yet**. That makes one class of change *free now and expensive later*: anything that becomes a **persisted on-device format**, a **DB schema**, an **external identifier**, or a **parser contract** is trivial to reshape today, but once shipped can only change through migrations, dedup-busting, or a forced app update. This document catalogs those items so they can be locked down before the first release, and flags the release-readiness gaps that would block or weaken a 1.0 ship.

Findings are ordered by **cost-of-deferral**, not by code area:

| Tier | Meaning |
|------|---------|
| **0** | Release blocker — Play rejects, or shipping is unsafe |
| **1** | Format / contract lock-in — only cheap to change *before* launch |
| **2** | Release-readiness — should land before ship (in scope) |
| **3** | Noted — lower priority / record so it isn't broken later |

Decisions already taken by the maintainer are marked **[decided]**.

---

## Tier 0 — Release blockers

### 0.1 `targetSdk` is below Google Play's minimum
- **What:** `app/build.gradle.kts:89` — `targetSdk = 34`. (`compileSdk = 36`, `minSdk = 26`.)
- **Why now:** Google Play requires **new app submissions and updates to target API 35** (enforced since 2025-08-31; the requirement tracks "within one year of the latest major Android release"). A `targetSdk = 34` upload is rejected at submission.
- **Recommend:** bump `targetSdk` to **35** (or 36, matching `compileSdk`). Then re-test what API 35 hardens: forced edge-to-edge layout, `POST_NOTIFICATIONS` behavior, foreground-service type enforcement, and any non-SDK interface use.
- **Risk:** edge-to-edge insets and notification permission are the most likely visual/behavioral regressions; verify the notification opt-in and OAuth flows after the bump.
- **Verify the claim** against the current Play policy page before publishing the release — the threshold ratchets yearly.

---

## Tier 1 — Format / contract lock-in (only cheap pre-release)

### 1.1 Collapse the migration history to a clean v1 baseline — **[decided: reset to v1]** *(headline)*
- **What today:**
  - `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/db/DomainDatabase.kt:42` — `DOMAIN_DB_VERSION = 22`, `exportSchema = true`.
  - `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/db/Migrations.kt:266` — `DOMAIN_MIGRATIONS` registers **17 migrations** `MIGRATION_5_6 … MIGRATION_21_22`; `DOMAIN_AUTO_MIGRATIONS` is empty (line 268).
  - `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/di/DomainModule.kt:76-77` — `.addMigrations(*DOMAIN_MIGRATIONS)` + `.fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)` (legacy pre-v5 only).
  - Exported schemas `domain/schemas/pm.bam.gamedeals.domain.db.DomainDatabase/5.json … 22.json`.
- **Why now:** No device anywhere holds schema v5–v21, so **those 17 migrations can never run in production.** They are dead code plus a migration test suite that obscures the actual baseline. This simplification is only available before launch — after the first install in the wild, v22 (or whatever ships) becomes a real baseline you must migrate *from*.
- **Change:**
  1. `DomainDatabase.kt`: `DOMAIN_DB_VERSION = 22` → `1`.
  2. `Migrations.kt`: delete every `MIGRATION_*` object; `DOMAIN_MIGRATIONS` → `emptyArray()`; leave `DOMAIN_AUTO_MIGRATIONS` empty.
  3. `DomainModule.kt`: drop `.addMigrations(*DOMAIN_MIGRATIONS)` (now empty) and the legacy `.fallbackToDestructiveMigrationFrom(..., 1,2,3,4)`. Replace with `.fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` so a dev device currently on v22 recreates cleanly when the code version drops to 1 (without it, Room throws on the apparent downgrade).
  4. `domain/schemas/.../DomainDatabase/`: delete `5.json … 22.json`; regenerate a single **`1.json`** baseline and commit it as the post-release contract.
  5. Delete the migration test suite under `domain/src/commonTest/...`. Before deleting, `grep` for `DOMAIN_DB_VERSION`, `MIGRATION_`, and `DOMAIN_MIGRATIONS` to catch references in tests/helpers.
- **Post-release policy (state in code comments + here):** from **v1 forward**, every schema change requires a real `Migration(n, n+1)` plus a schema-diff test. `fallbackToDestructiveMigrationOnDowngrade` is a *dev convenience only*.
- **Risk / guard:** do **not** add a blanket `fallbackToDestructiveMigration()` (all versions) to the release build — it would silently wipe real user data on any future broken migration. Downgrade-only is the safe net.
- **Trade-off accepted:** loses the schema-export git continuity; any dev device is wiped once on next launch.

### 1.2 Targeted parse resilience against upstream APIs — **[decided: targeted, no global Json change]**
External feeds (ITAD, IGDB, GamerPower) *will* change after launch. Today a single unexpected field value or unknown enum throws a `SerializationException` that fails the **entire** response, and there is no client-side recovery until you push an app update. The shared `Json` is already well-configured — keep it; do **not** add `coerceInputValues`/`isLenient` (they mask real upstream regressions globally). Apply surgical fixes at the model / decode boundary instead.

Shared `Json` (leave as-is): `common/src/commonMain/kotlin/pm/bam/gamedeals/common/di/CommonModule.kt` — `Json { encodeDefaults = true; ignoreUnknownKeys = true }`. `ignoreUnknownKeys = true` already makes the app tolerant to *added* fields. ✅

- **(a) Unknown-enum crash — highest probability break.**
  `remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/models/RemoteGiveaway.kt` — `RemoteGiveawayType` (`Game`/`DLC`/`Early Access`/`Other`) has **no fallback**. If GamerPower (a community-run feed) adds a new type (e.g. `"Bundle"`, `"Key"`), the *entire* Giveaways response fails to parse.
  - Add an `UNKNOWN` constant **and a custom `KSerializer`** applied via `@Serializable(with = …)` that maps any unrecognized string → `UNKNOWN`. A bare `UNKNOWN` constant alone still throws without `coerceInputValues`, so the custom serializer is required for the no-global-change approach.
  - Filter `UNKNOWN` out (or bucket it) in the GamerPower → domain mapper.

- **(b) Skip-and-log bad list elements.**
  List responses are all-or-nothing today: one malformed row kills the batch. Add a shared helper in the `remote` module (e.g. `decodeListSkippingInvalid<T>()`) that walks the JSON array, decodes each element in its own `try`/`catch`, and logs+drops failures while keeping the good rows. Apply at the high-value list boundaries:
  - `RemoteItadDealsResponse.list` (`remote/itad/.../models/RemoteItadModels.kt`)
  - the GamerPower giveaways array
  - IGDB game arrays (`remote/igdb/.../models/RemoteIgdbGame.kt`)

- **(c) Nullable defaults on all-required *display* DTOs.**
  These have non-nullable fields with no defaults; an omitted/null value crashes the row:
  - `RemoteGiveaway` (16 required fields) — default the non-essential strings/ints (`worth`, `thumbnail`, `image`, `description`, `instructions`, urls, dates, `status`, `platforms`, `users = 0`); keep **`id`** required (it's the key).
  - `RemoteItadPrice.amount` / `currency`, `RemoteItadSearchGame.title` (`remote/itad/.../models/RemoteItadModels.kt`).
  - `RemoteIgdbGame.id` / `name` — keep required, but covered by the skip-bad-element pass (b).

- **(d) Auth tokens are the deliberate exception — do *not* default them.**
  `RemoteItadTokenResponse.accessToken` (`remote/itad/.../auth/oauth/ItadOAuthModels.kt`) and `RemoteTwitchTokenResponse` (all 3 fields, `remote/igdb/.../auth/RemoteTwitchTokenResponse.kt`). A missing `access_token` is a *genuine* auth failure, not a recoverable display gap. Keep them required, but ensure the failure surfaces as a **typed auth error**, not a raw `SerializationException` bubbling to the UI.

### 1.3 Freeze persisted Storage keys & enum names as contracts
- **What:** 11 persisted `Storage` keys and the JSON-blob shapes behind them become immutable contracts the moment a user writes one:

  | Key | Owner | Shape |
  |-----|-------|-------|
  | `mature_opt_in` | `SettingsRepository` | Boolean |
  | `deals_filter` | `SettingsRepository` | `DealsFilter` JSON |
  | `onboarding_completed` | `SettingsRepository` | Boolean |
  | `selected_country_code` | `RegionRepository` | String |
  | `itad_auth_token` | `AuthTokenStore` | `StoredAuthToken` JSON *(see 2.2)* |
  | `background_notifications_enabled` | `NotificationSettings` | Boolean |
  | `surfaced_notification_ids` | `SurfacedNotificationStore` | `Set<String>` JSON |
  | `followed_deal_seen_signatures` | `FollowedDealSeenStore` | `Set<String>` (`gameId@price`) |
  | `followed_franchises` | `FollowedFranchiseRepository` | `List<FollowedFranchise>` JSON |
  | `followed_franchise_sale_snapshot` | `FranchiseSaleSnapshotStore` | `List<FranchiseSaleGame>` JSON |
  | `cache_schema_version` | `CacheMaintenance` | Int |

  Plus the enums serialized inside `deals_filter`: `ProductType`, `DealFlag`, `ReleaseWindow` (`domain/.../models/DealsFilter.kt`).
- **Why now:** renaming a key, or reordering/renaming a persisted enum constant, silently orphans real user state after launch (lost filters, lost follows, re-login).
- **Recommend (mostly a freeze, not code):** treat these key strings and enum constant names as **immutable**; any future rename must go through a read-time migration. The `deals_filter` enums already serialize by name/`@SerialName`, so they are safe **as long as constant names don't change**.
- **One real fragility to fix or guard:**
  `GiveawayPlatformsConverter` (`domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/utils/TypeAdapters.kt:49`) persists `GiveawayPlatform` via `GiveawayPlatform.valueOf(it)` over a comma-joined string — by **Kotlin constant name**, not `@SerialName`. Consequences: (1) renaming a constant breaks stored rows; (2) **an unknown platform name throws `IllegalArgumentException` on *read*.** Severity is lower because `Giveaway` is a TTL cache (re-fetched), but at minimum wrap the `valueOf` to skip/UNKNOWN unknown names so a new GamerPower platform can't crash giveaway hydration.
- **Existing good primitive (acknowledge, reuse):** `cache_schema_version` + `CacheMaintenance` already provide a one-shot cache-format reset lever. For any future blob-shape change, **bump that version** instead of writing a Room migration.

---

## Tier 2 — Release-readiness (in scope)

### 2.1 Enable R8 + author keep rules — **[decided: in scope]**
- **What today:** `app/build.gradle.kts:112-117` release block — `isMinifyEnabled = false`, no `shrinkResources`, only the default `proguard-android-optimize.txt`, no `proguard-rules.pro`. Ships un-minified: larger APK, dead code retained, easier to reverse-engineer.
- **Recommend:**
  - `release { isMinifyEnabled = true; shrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }`.
  - Author `app/proguard-rules.pro`. Reflection-sensitive surfaces to cover:
    - **kotlinx.serialization type-safe Navigation destinations** — `common/.../navigation/Destination.kt` `@Serializable` route classes are resolved by name; keep them and their generated serializers.
    - **Room** entities/DAOs — KSP-generated, mostly safe, but keep `@Entity`/`@Dao` types and the `@ProvidedTypeConverter` classes in `TypeAdapters.kt`.
    - **Coil3** and **Sentry** — both ship consumer R8 rules; verify, add app-specific keeps only if a release crash shows missing rules.
    - **Koin** here uses the constructor DSL (no reflection) → minimal/no rules needed.
- **Validation (must be a real release build, not just compile):** `./gradlew :app:assembleRelease` succeeds, then smoke-test on device: cold launch → ITAD OAuth redirect (`pm.bam.gamedeals://oauth/itad`) → tab navigation → background notification arming. Serialization/nav breakage from over-aggressive shrinking shows up only at runtime in the minified build.
- **Note:** this is *not* a data-format lock-in — it can technically land any time — but ship the minified artifact from 1.0 so the released behavior (and Baseline Profile timing) matches what you test.

### 2.2 Encrypt `itad_auth_token` at rest — **[decided: in scope]** (tracked: #239)
- **What today:** stored as **plain JSON** in SharedPreferences under key `itad_auth_token` (`domain/.../auth/AuthTokenStore.kt`); shape `StoredAuthToken{ accessToken, refreshToken, expiresAtEpochMs, username, scopeVersion }`. Bearer + refresh tokens in cleartext is an avoidable MASVS (MSTG-STORAGE) finding.
- **Why in scope now:** cheap to add; technically retrofittable later (the token is refreshable — worst case a user re-logs-in), but doing it before any real token is written avoids a one-time decrypt/re-encrypt migration.
- **Recommend:** encrypt **only this value** behind the common `Storage` abstraction via an Android `actual` — an Android Keystore-wrapped key encrypting the JSON blob. Note `androidx.security:security-crypto` (`EncryptedSharedPreferences`) is **deprecated / maintenance-mode**; prefer a Keystore-backed approach (or Tink) rather than adopting the deprecated lib. Place the secure backend in `androidMain`; keep the `commonMain` contract unchanged.
- **iOS:** Keychain `actual` deferred (no Mac available) — consistent with the project's existing Android-first platform split. State this explicitly so it isn't mistaken for an oversight.

### 2.3 Match ITAD's network resilience on IGDB & GamerPower — **[decided: in scope]**
- **What today:**
  - **ITAD** (`remote/itad/.../logic/ItadHttpClient.kt`) — `HttpRequestRetry` (retry on 429, `exponentialDelay(respectRetryAfterHeader = true)`, max 3) **and** a concurrency limiter (5). ✅
  - **IGDB** (`remote/igdb/.../logic/IgdbHttpClient.kt`) — concurrency limiter (8) but **no retry**.
  - **GamerPower** (`remote/gamerpower/.../di/RemoteNetworkModule.kt` + shared client) — **neither**.
- **Recommend:** add `HttpRequestRetry` to **IGDB**; add **both** retry and a concurrency limiter to **GamerPower**. Reuse the existing ITAD plugin configuration as the template. Pure robustness, no format lock-in.

---

## Tier 3 — Noted, lower priority

- **Central parse-failure recovery.** Parse exceptions are wrapped (`ApiResponse.exception`) and re-thrown per call site; there's no shared fallback policy. The 1.2(b) skip-bad-element helper covers the worst feed-level case; a broader "degrade gracefully" policy is backlog.
- **Notification dedup format.** `surfaced_notification_ids` and `followed_deal_seen_signatures` (`gameId@price`) drive dedup. Changing the signature scheme post-release causes a one-time burst of duplicate or missed alerts. Freeze the format; if it must change, pair it with a `cache_schema_version`-style reset.
- **Startup resilience (already good — record as a strength).** `GameDealsApplication.onCreate` runs Sentry init, DB warm, cache maintenance, and notification lifecycle as fire-and-forget on a `SupervisorJob` + `Dispatchers.IO`; failures are logged and never crash cold start. `CancellationException` is correctly re-thrown.
- **Persisted runtime contracts already correct — do not change post-release.** WorkManager unique work `"itad-notification-poll"` (6h, `UPDATE` policy, `CONNECTED`); notification channel `"itad_waitlist"`; fixed summary IDs `Int.MIN_VALUE` and `Int.MIN_VALUE + 1`; OAuth deep link `pm.bam.gamedeals://oauth/itad`; intent extras `extra_notification_route` / `notifications` / `followed_series`. These are correct today; the only action is *don't rename them*.
- **Secrets.** IGDB/ITAD keys come from `local.properties` (dev) / env vars (CI) → `BuildConfig` (`app/build.gradle.kts`), not hardcoded — acceptable for a client app. Be aware they're extractable from the APK; ITAD/IGDB API keys are low-sensitivity, but rotate if leaked and never commit `local.properties`.

---

## Pre-release checklist

**Tier 0 — blockers**
- [x] Bump `targetSdk` 34 → **36** (matches `compileSdk`); `enableEdgeToEdge()` added in `MainActivity`. ⚠ Still needs an on-device edge-to-edge / notifications / OAuth re-test.

**Tier 1 — format/contract lock-in (before first install in the wild)**
- [x] Reset Room to **v1**: version → 1, empty `DOMAIN_MIGRATIONS`, deleted `MIGRATION_*`, `fallbackToDestructiveMigrationOnDowngrade`, regenerated single `1.json`, deleted migration tests.
- [x] GamerPower `RemoteGiveawayType`: added `UNKNOWN` + custom serializer; bucketed to `OTHER` in mapper.
- [x] Added `decodeListSkippingInvalid<T>()` helper (`remote/.../logic/JsonListDecoding.kt`); applied to ITAD deals list, GamerPower giveaways, IGDB game arrays.
- [x] Added nullable defaults to all-required **display** DTOs (`RemoteGiveaway`, `RemoteItadPrice`, `RemoteItadSearchGame`); kept `id`/`name` required.
- [x] Kept auth-token DTOs required; a missing `access_token` now surfaces as a typed `ItadOAuthException`.
- [x] Guarded `GiveawayPlatformsConverter` against unknown platform names (skip instead of `valueOf` crash).
- [x] Recorded the 11 Storage keys + `deals_filter` enum names as frozen contracts (code comments).

**Tier 2 — release-readiness (in scope)**
- [x] Enabled R8 + `isShrinkResources`; added `app/proguard-rules.pro`; `assembleRelease` passes (on-device smoke test still pending).
- [x] Encrypted `itad_auth_token` via a `SECURE_QUALIFIER` store — Android Keystore AES/GCM **and** iOS Keychain (#239). *(Deviation from the original "iOS deferred": iOS Keychain backend implemented, pending macOS compile.)*
- [x] Added 429 retry to IGDB; added retry + concurrency limiter to GamerPower (shared `GameDealsConcurrencyLimiter`).

**Tier 3 — record, don't change**
- [x] Froze notification dedup signature format (record).
- [x] Froze WorkManager work name, notification channel/IDs, OAuth scheme, intent extras (record).

---

*Sources: four exploration passes over the persistence layer, serialization/network layer, and runtime/release config. All file references verified against the working tree at audit time (`dev` branch). Re-confirm cited line numbers before executing — the codebase moves.*
