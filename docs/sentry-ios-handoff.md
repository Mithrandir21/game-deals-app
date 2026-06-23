# Sentry iOS — implementation notes

> **Status: implemented & verified on the build/link side** (Xcode 26.5, macOS, arm64 simulator). The iOS-native
> §3 work described by the original handoff is done. The only thing that can't be exercised here is the
> **live-event smoke test**, which needs a real DSN — a secret that isn't in the repo (Android runs dormant for
> the same reason). See [What's verified](#whats-verified) and [Remaining (secret-gated)](#remaining-secret-gated).

## TL;DR of what was wired
1. **sentry-cocoa linked via SPM**, pinned to **8.58.2** (the exact cocoa version the KMP SDK 0.26.0 was compiled
   against). Product: **`Sentry-Dynamic`** (auto-embedded by SPM). `iosApp/iosApp.xcodeproj`.
2. **DSN plumbing**: `SENTRY_DSN` in `Secrets.xcconfig` (+ `.template`) → `SentryDsn` in `Info.plist` → read at
   runtime via the existing `infoPlistString(...)`.
3. **`startSentry()`** in `MainViewController.kt` (iosMain) calls the shared `configureSentryOptions(...)`; it is
   called **first** in the Swift `AppDelegate`. The anonymized install-id user is attached after Koin via
   `attachSentryUser()`.
4. **dSYM upload** Run Script build phase (env- + sentry-cli-guarded; uploads only in CI where the secrets exist).

## Key decisions (deviations from the original plan, with rationale)
- **No `sentry-kmp` version bump.** The original note suggested `0.26.0 → 0.27.0`. Verified against the SDK's
  `buildSrc/Config.kt`: **both 0.26.0 and 0.27.0 pin sentry-cocoa `8.58.2`** — the only difference is the
  Android-side sentry-java version. Bumping would needlessly re-touch the already-verified Android path, so we
  kept **0.26.0** and pinned SPM to the matching **8.58.2**. The lockstep is documented in `gradle/libs.versions.toml`.
- **`Sentry-Dynamic`, not `Sentry`.** The `Sentry` SPM product is a *static* xcframework; `Sentry-Dynamic` is the
  *dynamic* one. Our `ComposeApp` framework is static (`isStatic = true`). The dynamic Sentry product is what the
  official KMP SPM sample uses, is auto-embedded by SPM, and avoids the `-ObjC`/static-category linker pitfalls —
  so it's the robust pairing for a static Kotlin framework.
- **`startSentry()`, not `initSentry()`.** Kotlin/Native mangles any `init*` top-level function to `doInit*` in the
  generated ObjC/Swift API (Swift would see `MainViewControllerKt.doInitSentry()`). Renaming to `startSentry()`
  keeps the Swift call site clean and parallels the existing `startKoinIfNeeded()`.

## What's verified
On Xcode 26.5 / iOS 26.5 arm64 simulator:
1. `./gradlew :iosApp:linkDebugFrameworkIosSimulatorArm64` — Kotlin framework compiles with the Sentry calls. ✅
2. `xcodebuild … build` (Debug, arm64 sim) — **`** BUILD SUCCEEDED **`**; SPM resolved sentry-cocoa @ 8.58.2. ✅
3. Linkage: `iosApp.debug.dylib` carries `LC_LOAD_DYLIB @rpath/Sentry.framework/Sentry`, and the Kotlin cinterop's
   undefined ObjC class refs (`_OBJC_CLASS_$_SentryOptions`, `_SentrySDKInternal`, `_SentryScope`, …) resolve
   against the embedded dynamic `Sentry.framework`. ✅
4. Runtime: the app boots on the simulator without a startup crash, which means dyld loaded the embedded
   `Sentry.framework` successfully and `startSentry()` correctly no-ops on the empty DSN. ✅
5. `SentryDsn` key is present in the built `Info.plist` (empty → dormant, same as Android with no `sentryDsn`). ✅

> ⚠️ Build for an **arm64 simulator** (a concrete device id, not `generic/platform=iOS Simulator`). The project
> only declares `iosSimulatorArm64`, so a universal-sim build fails the Kotlin phase with `Unknown iOS simulator
> arch: 'x86_64'`. This is an invocation gotcha, not a code issue.

## Live-event smoke test — DONE
With a real DSN in `iosApp/Secrets.xcconfig`, a Debug simulator run transmitted both a session and a
`Sentry.captureMessage("ios sentry smoke test")` envelope: Sentry returned **HTTP 200** with a server-assigned
event id, tagged `environment=production`, `release pm.bam.gamedeals@1.0+1`. The temporary capture line +
`options.debug = true` were then reverted. (A Debug build sends because iOS gates only on DSN presence, not on
release/debug — unlike Android, which is release-only. See the gating note below.)

> **xcconfig `//` escape (this bit us):** the `$()` trick does **not** work in Xcode 26.5 — `SENTRY_DSN = https:$()//…`
> resolves to just `https:`. Use a single-slash variable so no line contains a literal `//`:
> ```
> SLASH = /
> SENTRY_DSN = https:$(SLASH)$(SLASH)publicKey@o…ingest.de.sentry.io/projectId
> ```

## Remaining (secret-gated)
1. **dSYM upload in CI.** The Run Script phase uploads only when `sentry-cli` is installed **and** `SENTRY_AUTH_TOKEN`
   is set (mirrors Android's `autoUploadProguardMapping` gate). Reuse the Bitrise `SENTRY_ORG` / `SENTRY_PROJECT` /
   `SENTRY_AUTH_TOKEN` secrets (`docs/ci-cd.md`); ensure `sentry-cli` is on the CI image.
2. **(Optional) Release/debug gating on iOS.** `startSentry()` currently arms whenever a DSN is present, so a local
   Debug build with a DSN in `Secrets.xcconfig` *will* send events (tagged `production`). Android instead early-returns
   on debuggable builds. If local Debug noise on the prod dashboard is unwanted, gate iOS on `Platform.isDebugBinary`
   too (the `currentRemoteBuildType()` helper already reads it).

## Note on committing
`iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` was generated by SPM resolution and
pins sentry-cocoa to 8.58.2 (exact revision). It is **not** gitignored — commit it alongside the project change so
CI resolves the identical version. `iosApp/Secrets.xcconfig` stays gitignored (local secrets); the `SENTRY_DSN`
line was added to the committed `Secrets.xcconfig.template`.

## iOS vs Android differences (for context)
- ANR is Android-only; iOS gets **App Hang** detection (`enableAppHangTracking`, on by default) — nothing to wire.
- Performance: same `tracesSampleRate` knob; sentry-cocoa auto-instruments app-start / screen transactions.

## Reference — files in play
| File | Role |
|---|---|
| `gradle/libs.versions.toml` | `sentry-kmp = 0.26.0`; lockstep note (↔ sentry-cocoa 8.58.2) |
| `logging/build.gradle.kts` | Sentry KMP dep in commonMain |
| `logging/src/commonMain/.../SentryConfig.kt` | shared `configureSentryOptions()` — called from both platforms |
| `logging/src/iosMain/.../di/LoggingIosModule.kt` | registers the `SentryLoggingListener` bridge for iOS |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | sentry-cocoa SPM ref (`Sentry-Dynamic` @ 8.58.2) + dSYM-upload phase |
| `iosApp/Secrets.xcconfig` (+ `Configuration/Secrets.xcconfig.template`) | `SENTRY_DSN` plumbing |
| `iosApp/iosApp/Info.plist` | `SentryDsn = $(SENTRY_DSN)` |
| `iosApp/src/iosMain/.../MainViewController.kt` | `startSentry()` + `attachSentryUser()` |
| `iosApp/iosApp/iOSApp.swift` | calls `startSentry()` first in `AppDelegate` |
| `docs/ci-cd.md` | `SENTRY_*` secrets reference |
