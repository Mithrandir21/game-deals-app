# Sentry iOS — Mac handoff

> **For the agent/engineer picking this up on a Mac.** You are on branch `feat/sentry-ios`.
> Android Sentry is **done and verified**; the **shared Kotlin** for iOS is **already in place**. Your
> job is the iOS-native work that can only happen on macOS + Xcode 16. Everything below is the remaining
> §3 ("iOS-native") work — the §1/§2 shared changes are committed.

## TL;DR
1. Link **sentry-cocoa** into the Xcode app target (SPM; CocoaPods fallback documented).
2. Plumb the **DSN** through `Secrets.xcconfig` + `Info.plist`.
3. Add an **iOS `initSentry()`** (iosMain) that calls the shared `configureSentryOptions(...)`, call it **first** in the Swift `AppDelegate`, and attach the install-id user after Koin.
4. Upload **dSYMs** for symbolication.
5. Build framework + app, run on a simulator, confirm an event reaches Sentry.
6. **Only then** is the branch mergeable to `dev` (see the hard rule at the bottom).

---

## What's already done (do NOT redo)

**Android (verified: assembleDebug / assembleRelease / installRelease + a live event landed):** DSN via the
secrets pipeline → `BuildConfig.SENTRY_DSN`; release-only init; anonymized install-id user; mapping upload
via `io.sentry.android.gradle`. See the first commit on this branch.

**Shared Kotlin (this branch, second commit):**
- `sentry-kotlin-multiplatform` is in `:logging` **commonMain** (`logging/build.gradle.kts`).
- `SentryLoggingListener` is in **commonMain** (`logging/src/commonMain/.../implementations/SentryLoggingListener.kt`) and **already registered in `loggingIosModule`** (`logging/src/iosMain/.../di/LoggingIosModule.kt`). It bridges the app `Logger` → Sentry; nothing more to wire.
- **`configureSentryOptions(options, dsn, release, dist, tracesSampleRate, environment="production")`** exists in `logging/src/commonMain/.../SentryConfig.kt`. It sets environment, sampling, `sendDefaultPii=false`, and the HTTP-breadcrumb URL scrubber. **Call this from iOS init too** — do not duplicate the policy.
- The anonymized **install-id** is `SettingsRepository.getInstallId()` (already works on iOS — common `Storage` over `NSUserDefaults`).

---

## §3 — iOS-native work (Mac / Xcode 16 only)

### 0. Preconditions & version pairing
- macOS + **Xcode 16**, sentry-cocoa **8.36.0+** (8.25+ for the Apple privacy manifest).
- **Pin a sentry-cocoa version compatible with the KMP SDK.** Check the compatibility table in the SDK README: https://github.com/getsentry/sentry-kotlin-multiplatform . **Recommended:** bump `sentry-kmp` `0.26.0 → 0.27.0` in `gradle/libs.versions.toml` and use **sentry-cocoa 8.58.2** (a documented-compatible pair). Mismatched versions are the #1 cause of link/runtime failures here.

### 1. Link sentry-cocoa (SPM — primary)
The project ships a **static** `ComposeApp` framework (`iosApp/build.gradle.kts`, `isStatic = true`) and uses
plain Xcode/SPM (no CocoaPods plugin). The `sentry-kotlin-multiplatform` iOS klib carries cinterop bindings
against Sentry-Cocoa; the symbols resolve when the **app target links Sentry-Cocoa**.

1. In `iosApp/iosApp.xcodeproj`: **File → Add Package Dependencies** → `https://github.com/getsentry/sentry-cocoa` → pin the version chosen in step 0.
2. Add the **`Sentry`** product to the app target's *Frameworks, Libraries, and Embedded Content*.
3. Build the app once in Xcode to confirm the Kotlin framework's Sentry symbols link.

**CocoaPods fallback** (if SPM linking fights you): add the Kotlin CocoaPods plugin to `:iosApp`
(`kotlin { cocoapods { pod("Sentry") { version = "…" } } }`), run `pod install`, open the `.xcworkspace`.
This is the "blessed" KMP-native pod path and links reliably, at the cost of introducing CocoaPods.

### 2. DSN plumbing (mirror the IGDB/ITAD pattern)
- Add to `iosApp/Secrets.xcconfig`: `SENTRY_DSN = https://…ingest.sentry.io/…`
  (⚠️ xcconfig treats `//` as a comment — escape the scheme, e.g. `https:/​/…`, per how the other secrets handle it, or use the `$()`-safe form already used in this file).
- Reference it in `iosApp/iosApp/Info.plist` as a new key, e.g. `SentryDsn` = `$(SENTRY_DSN)` (same mechanism as the IGDB/ITAD keys at the top of the plist).
- It's read at runtime via the existing `infoPlistString(...)` helper in `MainViewController.kt`.

### 3. iOS init + user attach
Add to `iosApp/src/iosMain/kotlin/pm/bam/gamedeals/iosApp/MainViewController.kt` (keeping it in this file
means Swift sees it as `MainViewControllerKt.initSentry()`, matching `startKoinIfNeeded()`):

```kotlin
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.User
import pm.bam.gamedeals.logging.configureSentryOptions
import platform.Foundation.NSBundle

private const val SENTRY_TRACES_SAMPLE_RATE = 1.0   // mirror Android; dial to ~0.2 once volume is known

fun initSentry() {
    val dsn = infoPlistString("SentryDsn").orEmpty()
    if (dsn.isEmpty()) return                        // no DSN → no-op (e.g. local dev without secrets)
    val shortVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0"
    val build = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "0"
    Sentry.init { options ->
        configureSentryOptions(
            options = options,
            dsn = dsn,
            release = "pm.bam.gamedeals@$shortVersion+$build",
            dist = build,
            tracesSampleRate = SENTRY_TRACES_SAMPLE_RATE,
        )
    }
}
```

Attach the install-id user **after Koin starts** (mirrors Android's `attachSentryUser`). Inside
`bootstrapKoin()`, after `startKoin { … }`, launch a coroutine on the existing app scope:

```kotlin
if (Sentry.isEnabled()) {
    appScope.launch {            // reuse whatever scope the lifecycle pollers use in this file
        runCatching {
            val installId = koin.get<SettingsRepository>().getInstallId()
            Sentry.setUser(User().apply { id = installId })
        }
    }
}
```

Then call `initSentry()` **first** in the Swift entry point so the crash handler arms before anything else
— `iosApp/iosApp/iOSApp.swift`, in `AppDelegate.application(_:didFinishLaunchingWithOptions:)`:

```swift
MainViewControllerKt.initSentry()                 // FIRST
MainViewControllerKt.startKoinIfNeeded()
NotificationBackgroundPollKt.registerNotificationBackgroundPoll()
```

> Verify the exact KMP API names against the SDK version you pin — `Sentry.init`, `Sentry.setUser`,
> `User`, `configureScope` are stable in 0.26/0.27, but double-check if you bump.

### 4. Symbolication (dSYM upload — NOT R8 mapping)
The Android Gradle plugin does nothing for iOS. Add a **Run Script** build phase (after "Embed Frameworks")
using `sentry-cli`, or the build-phase the Sentry SPM package documents:

```sh
export SENTRY_ORG="…" SENTRY_PROJECT="…" SENTRY_AUTH_TOKEN="…"   # same secrets as CI
sentry-cli debug-files upload --include-sources "$DWARF_DSYM_FOLDER_PATH"
```

Reuse the `SENTRY_ORG` / `SENTRY_PROJECT` / `SENTRY_AUTH_TOKEN` from the Android CI setup (`docs/ci-cd.md`).
Upload dSYMs for both the Kotlin `ComposeApp` framework and the app.

### 5. Verify
1. `./gradlew :iosApp:linkDebugFrameworkIosSimulatorArm64` (or build via Xcode) — confirms the framework links Sentry-Cocoa (this is the step that's been impossible on Linux).
2. Run the app on a simulator/device. Confirm Sentry init runs (no crash; the SDK loads).
3. Temporarily add `Sentry.captureMessage("ios sentry smoke test")` after init (or `Sentry.crash()` for an uncaught test); confirm the Issue appears in the **same Sentry project** as Android, tagged `environment=production`, with `release pm.bam.gamedeals@…`, user = install-id (no PII). **Remove the temporary line.**
4. Force a crash in a **release**-style build and confirm the trace is **symbolicated** (dSYMs uploaded).

### iOS vs Android differences (for context)
- ANR is Android-only; iOS gets **App Hang** detection (`enableAppHangTracking`, on by default) — nothing to wire.
- Performance: same `tracesSampleRate` knob; sentry-cocoa auto-instruments app-start / screen transactions.

---

## ⚠️ Hard rule: do not merge to `dev` until iOS links + verifies
Moving the Sentry dependency to `commonMain` (already done on this branch) means an **iOS build will fail to
link** until sentry-cocoa is wired (steps above). The Android build is unaffected. So this branch must NOT
reach `dev` until §3 is complete and an iOS event has landed. Land it as a single PR once iOS is verified.

## Reference — files in play
| File | Role |
|---|---|
| `logging/build.gradle.kts` | Sentry dep now in commonMain |
| `logging/src/commonMain/.../SentryConfig.kt` | shared `configureSentryOptions()` — call from iOS |
| `logging/src/commonMain/.../implementations/SentryLoggingListener.kt` | Logger→Sentry bridge (shared) |
| `logging/src/iosMain/.../di/LoggingIosModule.kt` | already registers the bridge for iOS |
| `iosApp/iosApp.xcodeproj` | add sentry-cocoa SPM package here |
| `iosApp/Secrets.xcconfig` + `iosApp/iosApp/Info.plist` | DSN plumbing |
| `iosApp/src/iosMain/.../MainViewController.kt` | add `initSentry()` + user attach; has `infoPlistString()` |
| `iosApp/iosApp/iOSApp.swift` | call `initSentry()` first in `AppDelegate` |
| `gradle/libs.versions.toml` | `sentry-kmp` version (consider bump to 0.27.0) |
| `docs/ci-cd.md` | `SENTRY_*` secrets reference |
