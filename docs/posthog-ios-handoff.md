# PostHog iOS — implementation handoff

> **Status: done — build-verified on macOS (Xcode 26.5, iOS 26.5 arm64 sim, 2026-06-24).** The §3 iOS-native
> work is complete: `posthog-ios` 3.38.0 linked via SPM (product `PostHog`), `Secrets`/`Info.plist` plumbing in,
> `startPostHog()` called in the `AppDelegate`. `BUILD SUCCEEDED` with the SDK linked (no `-ObjC`/symbol issues)
> and the app boots clean with `PostHog.setup()` running. **Pending:** the live `app_opened`/`screen` event check
> in the PostHog EU dashboard (deferred to a later session). Mirrors the completed
> [Sentry iOS handoff](sentry-ios-handoff.md).

## Context

PostHog is **product analytics** (manual events + screen tracking), not crash reporting. It's enabled in **all
variants** for now so event flow can be verified, with a one-line path to release-only later. Unlike Sentry there
is **no first-party PostHog KMP SDK** — we use the community wrapper `io.github.samuolis:posthog-kmp` (0.1.4),
hidden behind our own `Analytics` interface so it's swappable. The wrapper binds the official **posthog-ios** SDK,
which must be linked natively via SPM (same app-link story as sentry-cocoa).

## Already done (shared + Android — no Mac needed)
1. **Wrapper dep** in `gradle/libs.versions.toml` (`posthog-kmp = "0.1.4"`) + `:logging` commonMain and `:app`.
2. **`Analytics` seam** in `logging/src/commonMain/.../analytics/`: `Analytics`, `NoOpAnalytics`,
   `PostHogAnalytics` (merges `environment`/`app_version` base props onto every event), `AnalyticsConfig`,
   `AnalyticsEvents`, and the shared `configurePostHog()` (EU host; **all autocapture/lifecycle OFF** so every
   event is one we emit and is env-tagged).
3. **Koin binding** in `loggingIosModule` (and `loggingAndroidModule`): `single<Analytics>` → `NoOpAnalytics`
   when the key is empty, else `PostHogAnalytics`.
4. **iOS Kotlin** in `MainViewController.kt`: `AnalyticsConfig` registered from `Info.plist` (`PostHogApiKey`),
   the Swift-facing `startPostHog()`, and `startAnalytics()` (identify install-id + `app_opened`) after Koin.
   > ⚠️ The `AnalyticsConfig` registration is what keeps the shared Koin graph valid on iOS — `WaitlistRepository`
   > now depends on `Analytics`. With no key it resolves to `NoOpAnalytics`, so the app is safe **before** the SPM
   > link below is done.

## iOS-native work — DONE (build-verified 2026-06-24)
1. **✅ Linked posthog-ios via SPM.** Added the PostHog Swift package to `iosApp.xcodeproj` pinned `exactVersion`
   **3.38.0** (the pin `posthog-kmp` 0.1.4 binds via spmForKmp — `libs.versions.posthog-ios` in the wrapper),
   product **`PostHog`**. The static product links cleanly with the static Kotlin framework + the `PostHogBridge`
   cinterop — **no `-ObjC`/dynamic-product workaround was needed** (unlike Sentry).
2. **✅ Key plumbing.** `POSTHOG_API_KEY` added to `Secrets.xcconfig.template` (real `Secrets.xcconfig` already had
   the `phc_…` value) → `PostHogApiKey` in `Info.plist` → read at runtime via `infoPlistString("PostHogApiKey")`.
   No `//` escaping needed (the `phc_…` key has none; host is hardcoded `HOST_EU` in `configurePostHog()`).
3. **✅ Call `startPostHog()` in the Swift `AppDelegate`** (`iOSApp.swift`) — placed **between `startSentry()` and
   `startKoinIfNeeded()`**, so `PostHog.setup()` runs before `startAnalytics()` (invoked from `bootstrapKoin()`)
   applies consent/opt-in. identify + `app_opened` fire automatically once a key is present and consent is given.

## Verification (on the Mac)
> **2026-06-24:** steps 1–2 **PASS** (`:iosApp:compileKotlinIosSimulatorArm64` green; `xcodebuild` Debug for the
> iPhone 17 arm64 sim `BUILD SUCCEEDED` with `posthog-ios` linked; app installs and boots clean). Step 3 (live
> dashboard event) **pending** — deferred to a later session.

1. `./gradlew :iosApp:linkDebugFrameworkIosSimulatorArm64` — Kotlin framework compiles with the PostHog calls.
   > Build for a **concrete arm64 simulator id**, not `generic/platform=iOS Simulator` (the project only declares
   > `iosSimulatorArm64`) — same invocation gotcha as the Sentry handoff.
2. `xcodebuild … build` (Debug, arm64 sim) — SPM resolves posthog-ios; `BUILD SUCCEEDED`.
3. Runtime: with a real `phc_…` key in `Secrets.xcconfig`, a Debug simulator run should show `app_opened` (+ a
   `screen` once you navigate) in the **PostHog EU** project's live-events view, tagged `environment=debug` and
   carrying the anonymized install-id `distinct_id`. With an empty key the app boots normally (NoOp).

## Gating note (matches Android)
`startPostHog()` arms whenever a key is present (all variants) — it does **not** gate on `Platform.isDebugBinary`.
To make it release-only later (like Sentry), add `if (Platform.isDebugBinary) return` to `startPostHog()`, and the
matching `if (isDebuggable()) return` to Android's `initPostHog()`.

## Consent (GDPR opt-out by default) — DONE in shared code, iOS inherits it
Analytics now **starts opted out** (`configurePostHog(optOut = true)`, a native field on v0.1.4's `PostHogConfig`)
and only turns on once the user consents. Consent is persisted in `SettingsRepository.setAnalyticsConsent()`,
which is the single point that flips PostHog's native opt-out (`Analytics.setConsent()` → `PostHog.optIn()/optOut()`)
and re-identifies on grant. The consent UI lives in `commonMain` Compose — an onboarding slide and an Account-page
toggle — so **iOS gets all of it for free** once the SPM link below lands. iOS `startAnalytics()` already only
identifies / emits `app_opened` when `getAnalyticsConsent()` is true (mirrors Android), so a no-consent launch
sends nothing. Remaining follow-up: replace the in-copy disclosure's `TODO(privacy)` with a hosted privacy-policy
link before public release.

## Reference — files in play
| File | Role |
|---|---|
| `gradle/libs.versions.toml` | `posthog-kmp = 0.1.4` |
| `logging/build.gradle.kts` | PostHog KMP dep in commonMain |
| `logging/src/commonMain/.../analytics/*` | `Analytics` seam + `configurePostHog()` (shared, both platforms) |
| `logging/src/iosMain/.../di/LoggingIosModule.kt` | registers `single<Analytics>` for iOS |
| `iosApp/src/iosMain/.../MainViewController.kt` | `startPostHog()` + `startAnalytics()` + `AnalyticsConfig` from Info.plist |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | **TODO**: posthog-ios SPM ref |
| `iosApp/Secrets.xcconfig` (+ template) | **TODO**: `POSTHOG_API_KEY` plumbing |
| `iosApp/iosApp/Info.plist` | **TODO**: `PostHogApiKey = $(POSTHOG_API_KEY)` |
| `iosApp/iosApp/iOSApp.swift` | **TODO**: call `startPostHog()` in `AppDelegate` |
