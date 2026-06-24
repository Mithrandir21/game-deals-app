# PostHog iOS — implementation handoff

> **Status: deferred — Mac-gated.** The shared Kotlin and the Android path are wired and verified; the iOS-native
> §3 work below (SPM link + `Secrets`/`Info.plist` plumbing + the `AppDelegate` call) needs a Mac with Xcode and
> can't be done in this Linux environment. This mirrors the original Sentry iOS handoff — see
> [docs/sentry-ios-handoff.md](sentry-ios-handoff.md) for the analogous, now-completed runbook.

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

## Remaining iOS-native work (do on a Mac)
1. **Link posthog-ios via SPM.** Add the PostHog Swift package to `iosApp/iosApp.xcodeproj`. Pin to the exact
   `posthog-ios` version the wrapper 0.1.4 was compiled against — check the wrapper's build config for the pin
   (the analogue of how sentry-kmp pins sentry-cocoa `8.58.2`); using a mismatched version risks link/symbol
   errors. If the static-framework `-ObjC` linker pitfalls bite (they did for Sentry), prefer the **dynamic**
   product, matching the `Sentry-Dynamic` decision.
2. **Key plumbing.** Add `POSTHOG_API_KEY` to `iosApp/Secrets.xcconfig` (+ commit the line to
   `Secrets.xcconfig.template`) → `PostHogApiKey` in `Info.plist` → already read at runtime via `infoPlistString("PostHogApiKey")`.
   > The xcconfig `//` escape bit the Sentry work — but a PostHog `phc_…` key has no `//`, so the host URL is the
   > only place it could matter, and the host is hardcoded to `HOST_EU` in `configurePostHog()` (not in xcconfig).
3. **Call `startPostHog()` first in the Swift `AppDelegate`** (`iOSApp.swift`), alongside the existing
   `startSentry()`. `startAnalytics()` is already invoked from `bootstrapKoin()`, so identify + `app_opened` fire
   automatically once a key is present.

## Verification (on the Mac)
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
