# Security Audit — MASVS-aligned

**Status:** findings only — no code changes have been made.
**Audience:** maintainer, pre-1.0.
**Date:** 2026-06-21.
**Standard:** OWASP MASVS / MASTG (test IDs cited where confident).

## Why this exists

A structured pass over the app's security posture, mapped to MASVS categories so findings track a recognized
standard. Scope: manifest & build config, the ITAD OAuth flow (PKCE/state/token exchange), token storage, the
Ktor network clients, WebView, logging, exported components, deep links, PendingIntents, backup rules, and
secrets handling. The app handles **ITAD OAuth tokens** (waitlist / collection / notes / profile) — those are the
crown jewels and drive the risk ranking.

Some items overlap the pre-release hardening pass (`docs/release-hardening-audit.md`) — that doc frames them by
cost-of-deferral; this one frames them by security impact. Cross-references are noted inline.

## Summary

| Category | Critical | High | Medium | Low |
|---|---|---|---|---|
| Storage | 0 | 1 | 2 | 0 |
| Crypto | 0 | 0 | 0 | 0 |
| Auth | 0 | 0 | 0 | 1 |
| Network | 0 | 0 | 1 | 2 |
| Platform | 0 | 0 | 1 | 1 |
| Code | 0 | 1 | 2 | 0 |
| Resilience | 0 | 0 | 0 | 0 |

**2 High, 6 Medium, 4 Low. No Critical.** The OAuth *protocol* implementation is genuinely strong (see "What's
already solid"). The weaknesses are around **where tokens rest, what leaves the binary, and release-build
hygiene** — not the auth handshake.

---

## MASVS-STORAGE

### S1 — OAuth access + refresh tokens stored unencrypted *(High)* — **REMEDIATED IN CODE (2026-06-24)**
> **Update:** #239 is implemented and wired. `AuthTokenStore` now resolves the `SECURE_QUALIFIER` `Storage`
> (`DomainModule.kt`), backed by **Android Keystore AES/GCM** (`EncryptedSharedPreferencesBackend`) and the
> **iOS Keychain** (`KeychainBackend`) — the iOS half macOS compile-verified. The risk description below reflects
> the pre-#239 state. Residual: runtime round-trip verification (a token is written only on login) rides on the
> deferred live OAuth smoke test.

`AuthTokenStoreImpl` persisted the full token set via `Storage` → plain `SharedPreferences` on Android /
`NSUserDefaults` on iOS (pre-#239). The code already flagged this (`domain/.../auth/AuthTokenStore.kt:18`; tracked
as #239, and as 2.2 in the release-hardening audit).

- **Issue:** `accessToken` + long-lived `refreshToken` + username sit in cleartext in `gamedeals_common_storage.xml`
  (`common/src/androidMain/.../di/CommonAndroidModule.kt:14`).
- **Risk:** High. On a rooted/compromised device, via `adb backup` (see S2), or a same-UID exploit, the refresh
  token grants durable access to the user's ITAD account — and logout doesn't revoke it server-side (A1).
- **Fix:** Android → Keystore-derived AES-GCM key wrapping the JSON blob (prefer this over the deprecated
  `androidx.security:security-crypto` / `EncryptedSharedPreferences`); iOS → Keychain
  (`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`). Keep the `Storage` interface; swap only the platform backend so
  only the auth key is encrypted.
- **Reference:** MASTG-TEST-0011 / MASVS-STORAGE-1.

### S2 — `allowBackup="true"` includes the token prefs *(Medium)*
`app/src/main/AndroidManifest.xml:11` sets `allowBackup="true"`; `backup_rules.xml` and `data_extraction_rules.xml`
are the untouched template stubs (everything included by default).

- **Risk:** Medium — compounds S1. The unencrypted token prefs flow into Auto Backup (Google cloud) and
  device-to-device transfer; on any debuggable build `adb backup` extracts them with no root. The credential leaves
  the device trust boundary.
- **Fix:** `android:allowBackup="false"`, **or** keep backup and exclude the auth file in both rule files —
  `<exclude domain="sharedpref" path="gamedeals_common_storage.xml"/>` under `<cloud-backup>` *and*
  `<device-transfer>` in `data_extraction_rules.xml`, plus `<full-backup-content>` for pre-API-31.
- **Reference:** MASTG-TEST-0009.

### S3 — Release logging is not stripped *(Medium)*
`SimpleLoggingListener.isEnabled()` returns `true` unconditionally
(`logging/src/androidMain/.../implementations/SimpleLoggingListener.kt:16`) and writes through `android.util.Log`
in all build types. It is registered for every build (`logging/src/androidMain/.../di/LoggingModule.kt:11`).

- **Risk:** Medium — no token is logged *today*, but every `logger.log(...)` across the app reaches logcat in
  production; any future "log the response/state" line silently becomes a data leak. (Ktor's wire `Logging` is
  correctly DEBUG-gated and capped at `LogLevel.HEADERS` — this is the *app* logger, which isn't gated.)
- **Fix:** Gate `SimpleLoggingListener` on `BuildConfig.DEBUG` (no-op in release); keep `SentryLoggingListener` for
  release crash reporting.

---

## MASVS-CODE

### C1 — IGDB client secret embedded in the APK *(High)*
`BuildConfig.IGDB_CLIENT_SECRET` is injected (`app/build.gradle.kts:99`) and used for a `client_credentials` grant
(`remote/igdb/.../auth/IgdbTokenProvider.kt:33`).

- **Issue:** A *confidential* OAuth client secret shipped in a public binary is extractable — and with minify off
  (C2) it's a literal string in `BuildConfig`. Anyone can mint Twitch/IGDB app tokens as this app → quota abuse,
  cost, and Twitch app-suspension risk.
- **Fix:** A mobile app is a *public* client and can't hold this secret. Move the IGDB `client_credentials` mint
  behind a small backend proxy that holds the secret and returns short-lived app tokens. (ITAD's OAuth correctly
  uses PKCE with a public `client_id` — that one is fine to ship.)
- **Reference:** MASTG-TEST-0014 / MASVS-CODE.

### C2 — `isMinifyEnabled = false` in release *(Medium)*
`app/build.gradle.kts:113`. No R8 shrinking or obfuscation; no `proguard-rules.pro`. Decided "in scope" as 2.1 in
the release-hardening audit.

- **Risk:** Medium — embedded secrets (C1, C3) and app logic are trivially readable in the shipped APK.
- **Fix:** `isMinifyEnabled = true` + `shrinkResources = true` + R8, with keep rules for kotlinx-serialization
  Navigation destinations, Room, Coil3, Sentry as needed (see release-hardening audit 2.1 for the keep-rule plan).

### C3 — ITAD API key embedded in the APK *(Medium)*
`BuildConfig.ITAD_API_KEY` (`app/build.gradle.kts:100`). Lower value than C1 (gates mostly public catalog data) but
still a shared secret extractable from the binary; rotate-and-proxy when convenient. `ITAD_OAUTH_CLIENT_ID` is a
public PKCE client id by design — not a finding.

---

## MASVS-NETWORK

### N1 — `usesCleartextTraffic="true"` with no Network Security Config *(Medium)*
`app/src/main/AndroidManifest.xml:19`. Every code endpoint is HTTPS (IGDB, Twitch, ITAD, GamerPower), and
Coil/Custom Tabs handle their own transport — so nothing is *currently* cleartext. But the flag permits a silent
HTTP downgrade for any future or image URL, with no declarative enforcement. Note the asymmetry: **iOS Info.plist
has no ATS exception** (HTTPS enforced) — only Android is opened up.

- **Fix:** `usesCleartextTraffic="false"` + a `network_security_config.xml` with
  `<base-config cleartextTrafficPermitted="false">`, referenced via `android:networkSecurityConfig`.
- **Reference:** MASTG-TEST-0019.

### N2 — ITAD bearer attached with `sendWithoutRequest { true }` *(Low)*
`remote/itad/.../logic/ItadAuthHttpClient.kt:80` sends the user's access token on every request unconditionally.
All calls are relative to `ITAD_BASE_URL`, so it's practically scoped — but a cross-host redirect would resend the
token. The IGDB client already does this right (scopes attachment to `api.igdb.com`,
`remote/igdb/.../logic/IgdbHttpClient.kt:34`).

- **Fix:** Mirror IGDB — restrict `sendWithoutRequest` to `host == ITAD_HOST`.

### N3 — No certificate pinning *(Low)*
Defense-in-depth for the token-bearing ITAD API. Optional given the data sensitivity and the pin-rotation burden.
If adopted, use a declarative `<pin-set>` in the Network Security Config with a backup pin and a ship-ahead rotation
plan.

---

## MASVS-PLATFORM

### P1 — Exported OAuth redirect on a hijackable custom scheme *(Medium)*
`OAuthRedirectActivity` is `exported="true"` on `pm.bam.gamedeals://oauth`
(`app/src/main/AndroidManifest.xml:34`), reached via a plain `ACTION_VIEW` browser intent
(`remote/itad/src/androidMain/.../oauth/AndroidAuthBrowserLauncher.kt:25`).

- **Issue:** A co-installed malicious app can register the same custom scheme and intercept the redirect (auth
  `code`), and any app can invoke the exported activity with a forged URI delivered to the process-global
  `AuthRedirectBus`.
- **Why only Medium, not High:** **strongly mitigated** — PKCE/S256 means an intercepted `code` is useless without
  the `code_verifier`, and the `state` is validated (`remote/itad/.../ItadLoginSourceImpl.kt:44`), so a
  forged/injected redirect fails. Residual risk is interception/DoS and deviation from best practice.
- **Fix:** Use **Android App Links** (an `https://` redirect with `autoVerify` + Digital Asset Links) so the scheme
  can't be claimed by another app, and/or launch via Custom Tabs. Keep PKCE + state.
- **Reference:** MASTG-TEST-0028 (deep link handling).

### P2 — In-app WebView has JS enabled + default file access *(Low — route currently unused)*
`feature/webview/src/androidMain/.../ui/WebView.android.kt:69` sets `javaScriptEnabled = true` and leaves
`allowFileAccess`/`allowContentAccess` at defaults. **Nothing navigates to `Destination.WebView` today** — all
"open web" actions go through Custom Tabs / `SFSafariViewController` via `openInApp` (good), so this is latent.

- **Fix before first use:** `allowFileAccess = false`, `allowContentAccess = false`, enable JS only if the loaded
  page needs it, and constrain loads to `https` hosts. No `addJavascriptInterface` present — keep it that way.

---

## MASVS-AUTH

### A1 — Logout clears local tokens but does not revoke server-side *(Low)*
`logout` → `authTokenStore.clear()` (`remote/itad/.../auth/oauth/ItadTokenProvider.kt:55`) removes local tokens and
clears cached Room data (good hygiene), but issues no revoke call. The refresh token stays valid at ITAD until
expiry.

- **Fix:** If ITAD exposes a token-revocation endpoint, call it on logout; otherwise document the residual window.
  Lower priority than S1 — the storage fix is what actually exposes the token.

---

## What's already solid (don't regress these)

- **OAuth is textbook:** PKCE with **S256**, verifier + state from the platform CSPRNG (`SecureRandom` /
  `SecRandomCopyBytes`, `remote/itad/.../auth/oauth/Pkce.kt:25` + `SecureRandom.*`), and **`state` is validated** on
  return (`remote/itad/.../ItadLoginSourceImpl.kt:44`). This is the part most apps get wrong.
- **PendingIntents** use `FLAG_IMMUTABLE` (`app/.../notifications/AndroidNotificationPresenter.kt:125`).
- **Ktor wire logging** gated to `RemoteBuildType.DEBUG` and capped at `LogLevel.HEADERS` (never BODY).
- **iOS** enforces ATS (no cleartext bypass), uses `ASWebAuthenticationSession` + `SFSafariViewController`
  (isolated browser contexts).
- **Secrets aren't committed** — `local.properties`, `*.jks`, `*.keystore`, `Secrets.xcconfig` all gitignored; only
  a `.template` is tracked.
- **Minimal permissions** — `INTERNET` + `POST_NOTIFICATIONS` only. No location/contacts/storage.
- **IGDB bearer scoped to its host**; sane timeouts + `expectSuccess`.

---

## Recommended remediation order

1. ~~**S1 — encrypt the token store** (Keystore / Keychain).~~ **DONE in code (#239)** — Android Keystore AES/GCM + iOS Keychain via `SECURE_QUALIFIER`; iOS macOS compile-verified 2026-06-24. Runtime round-trip pending live OAuth login.
2. **S2 — exclude tokens from backup** (or `allowBackup=false`). One line; closes the off-device path that makes S1
   exploitable without root. Do alongside S1.
3. **C1 — move the IGDB client secret to a backend proxy.** Removes a confidential secret from the binary.
4. **C2 — enable R8/minify** in release (also shrinks the blast radius of C1/C3 while they're in flight).
5. **N1 — `usesCleartextTraffic=false` + Network Security Config.** Quick; brings Android to parity with iOS.
6. **S3 — gate the app logger on `BuildConfig.DEBUG`.** Quick; prevents future leaks.
7. **P1 — App Links for the OAuth redirect.** Hardening; PKCE+state already cover the real risk.
8. **C3, N2, N3, P2, A1** — opportunistic hardening.

---

*Sources: a structured MASVS pass over the manifest/build config, OAuth + token layer, network clients, WebView,
logging, and exported components. All file references verified against the working tree at audit time (`dev`
branch). Re-confirm cited line numbers before executing — the codebase moves.*
