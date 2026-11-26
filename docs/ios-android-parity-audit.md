# iOS ↔ Android feature-parity audit

**Date:** 2026-06-22
**Scope:** Recently-added features and the cross-cutting wiring that hosts them.
**Verdict:** The shared UI/domain layer is at parity. The gaps are all in **per-platform shell wiring and app-lifecycle reconciliation** — code that lives outside `commonMain` and therefore has no compiler check forcing the two platforms to agree. The iOS shell (`MainViewController.AppNavHost`) is a hand-maintained parallel of the Android `NavGraph` + `GameDealsApplication`, and it has drifted.

> **Status (2026-06-22):** Findings 1–3 implemented and **device-verified** by the maintainer; also compile-checked on iOS (`:iosApp`, `:remote:itad` simulator targets) and Android (`:app`). Finding 4 is partially addressed — the notification-lifecycle reconciliation was hoisted into shared `:domain` — with the full shell de-duplication left as a deferred follow-up. See per-finding "Resolution" notes below.

## Method

- Diffed Android `app/.../navigation/NavGraph.kt` + `GameDealsApplication.kt` + `MainActivity.kt` against iOS `iosApp/.../MainViewController.kt` + `iOSApp.swift` + `ContentView.swift`.
- Enumerated every `expect` in `commonMain` and confirmed each has an iOS `actual`.
- Compared the Koin module lists registered on each platform.
- Traced the two recently-added cross-cutting features end-to-end on both platforms: the actionable sign-in prompt, and the background notification poll.

---

## What IS at parity (the boundary of the problem)

- **expect/actual:** all pairs have iOS actuals and they are real implementations, not stubs — platform actions (SFSafariViewController/share/settings), notification permission (UNUserNotificationCenter), theme, webview (WKWebView), locale date formatting, region detector, secure random, Ktor http client, Room DB builder.
- **Screens:** every feature screen is registered in the iOS `NavHost` (home, deals, discover, account, game, giveaways, bundles, store, webview, onboarding).
- **In-screen bottom sheets:** deals filter, game, giveaways, region picker, onboarding, game peek — all `commonMain`, all work on iOS. (Only the *shell-level* sheet is missing — see Finding 1.)
- **Deep-link / notification routing:** `NotificationRouteBus` is mirrored (iOS `AppDelegate` → `deliverNotificationRoute` → bus; `AppNavHost` collects it).
- **SearchController** and **onboarding first-run/start-destination gating:** both collected/mirrored on iOS.
- **Background notification infrastructure:** `BGTaskScheduler` handler registration, `Info.plist` (`UIBackgroundModes=fetch`, `BGTaskSchedulerPermittedIdentifiers`), notification presenter, and the poll body are all present and correctly configured on iOS.
- **OAuth configuration:** redirect URI `pm.bam.gamedeals://oauth/itad`; scheme `pm.bam.gamedeals` matches `Info.plist` `CFBundleURLSchemes`; `itadIosModule` (the `ASWebAuthenticationSession` launcher) is registered in `MainViewController`.

---

## Findings

### 1. (HIGH — the reported bug) iOS never renders `SignInPromptHost()`

**This is the root cause of the reported symptom.** Tapping a gated ITAD action on iOS does nothing.

- Android renders the shell-level sign-in sheet at `app/src/main/java/pm/bam/gamedeals/navigation/NavGraph.kt:170` (`SignInPromptHost()`).
- iOS `iosApp/src/iosMain/.../MainViewController.kt` (`AppNavHost`) builds the same shell and the same `NavHost`, screen-for-screen, but **omits the `SignInPromptHost()` call**.

Mechanism: a gated tap (waitlist / collection / ignore / note) on a shared screen calls `SignInPromptController.request()`. That controller is a `SharedFlow` (`replay = 0`, buffer 1, drop-oldest) — it only does anything **if something is collecting it**. The sole collector lives inside `SignInPromptHost()` (`feature/account/.../SignInPromptHost.kt:50`). With no host on iOS, the emission lands in a 1-slot buffer with no consumer and is silently dropped.

**Fix direction:** add `SignInPromptHost()` to the iOS shell content lambda, mirroring `NavGraph.kt:170` (one call + one import). It belongs inside `GameDealsAppShell { ... }`, after the `NavHost` block.

**Caveat:** adding the host makes the sheet appear, but the sign-in it launches is itself unverified on iOS — see Finding 2.

**Resolution (2026-06-22):** Fixed — `SignInPromptHost()` added to `MainViewController.AppNavHost` after the `NavHost`. Device-verified: the sheet now appears on gated taps.

---

### 2. (HIGH — unverified) ITAD OAuth sign-in has never been exercised on iOS

Confirmed with the user: sign-in has never been attempted on iOS by any path. The machinery is all present and correctly configured (see "What IS at parity"), but unproven at runtime — and it backs **two** entry points:

- the (currently missing) `SignInPromptHost` sheet, and
- the Account-tab **"Sign in" / "Reconnect"** button (`feature/account/.../AccountScreen.kt:235`, `onReconnect = onLogin`), which is **reachable on iOS today** and calls the same `accountRepository.login()`.

So the "sync your games / sign in" capability is effectively unproven on iOS as a whole, not just behind the missing sheet.

Known risk flagged in the code itself: `remote/itad/.../IosAuthBrowserLauncher.kt` KDoc states it has never been smoke-tested on device/simulator and calls out an iPad presentation-anchor caveat (issue #144) — `UIApplication.sharedApplication.keyWindow` may be null / need scene-aware handling.

**Action:** smoke-test the full OAuth round-trip on a simulator/device (PKCE → ASWebAuthenticationSession → code exchange → `/user/info` → token persist) once the host is added — don't assume it works because it compiles.

**Resolution (2026-06-22):** OAuth round-trip device-verified working on iOS (both the new sheet and the Account-tab button). The presentation anchor in `IosAuthBrowserLauncher` was also hardened proactively — it now resolves the foreground-active `UIWindowScene`'s window instead of the deprecated `UIApplication.keyWindow`, so the iPad/multi-scene caveat (#144) is covered.

---

### 3. (MEDIUM) Notification lifecycle is not reconciled on iOS app-launch or logout

Android `GameDealsApplication.runNotificationLifecycle()` (`GameDealsApplication.kt:184`) observes `AuthTokenStore.observeAuthState()` and runs `applyNotificationLifecycle()` on every cold start. That does two things (`app/.../notifications/NotificationLifecycle.kt`):

1. idempotently **re-arms** the poll if opted-in (`schedule()`), which "also covers app start / reboot"; and
2. **clears the surfaced-id set on logout** so a different account re-alerts cleanly.

iOS `AppDelegate.didFinishLaunchingWithOptions` only **registers** the BG handler — it never calls `scheduler.schedule()` on launch, and there is no app-level auth-state observer. Consequences:

- **Re-arm fragility:** on iOS the poll re-arms *only* from inside the BG task handler (`NotificationBackgroundPoll.kt:46`). If that chain ever breaks — reboot, force-quit, or iOS dropping/never-running the one-shot `BGAppRefreshTaskRequest` — the poll stops and never recovers until the user re-toggles the opt-in. Android self-heals on every launch.
- **Logout dedupe:** the surfaced-id set is never cleared on logout on iOS, so switching ITAD accounts can suppress alerts that should re-fire.

Note: the opt-in *toggle* path is shared (`OnboardingViewModel`, `NotificationSettingsViewModel` both call `schedule()`/`cancel()`), so first opt-in works on iOS. It's the resilience + logout reconciliation that's missing.

**Fix direction:** add an iOS app-launch observer (in `MainViewController` bootstrap or `AppDelegate`) that mirrors `applyNotificationLifecycle` over `observeAuthState()`. Consider hoisting `applyNotificationLifecycle` out of `:app` into shared domain so both platforms call one implementation.

**Resolution (2026-06-22):** Fixed — `applyNotificationLifecycle` was hoisted from `:app` into shared `:domain` (`scheduling/NotificationLifecycle.kt`); Android's `GameDealsApplication` and its unit test now reference the shared copy, and `MainViewController.bootstrapKoin()` runs an `observeAuthState()` collector calling the same function. iOS now re-arms the poll on every (incl. background) launch and clears the surfaced-id set on logout — one implementation, both platforms.

---

### 4. (LOW — structural) The iOS shell duplicates Android's NavGraph with no parity guard

`MainViewController.AppNavHost` is a hand-copied parallel of `NavGraph.kt`; `GameDealsApplication.onCreate` startup work is partly re-implemented in `AppDelegate`/`bootstrapKoin`. Nothing forces the two to stay in sync — Finding 1 is the proof that they drift silently. Worth considering extracting a shared `AppNavHost`/startup-reconciliation into `commonMain` so both platforms call one source of truth, leaving only genuinely platform-specific bits (Koin bootstrap, image loader, `BGTaskScheduler`) per-platform.

**Resolution (2026-06-22):** Partially addressed — the notification-lifecycle reconciliation (Finding 3) is now a single shared implementation both platforms call. The larger shared-shell extraction (`AppNavHost`/`NavGraph`) is intentionally **deferred** to its own focused change rather than bundled with these fixes.

---

## Recommended order of operations (when fixes are greenlit)

1. ~~**Finding 1** — add `SignInPromptHost()` to iOS (tiny, unblocks the reported bug).~~ ✅ Done.
2. ~~**Finding 2** — smoke-test the OAuth round-trip on iOS via both the Account tab and the new sheet; fix the iPad/keyWindow anchor if it surfaces.~~ ✅ Done (verified + anchor hardened).
3. ~~**Finding 3** — add the iOS launch/logout notification reconciliation.~~ ✅ Done (hoisted to shared `:domain`).
4. **Finding 4** — optionally de-duplicate the shell to prevent the next drift. ⏳ Deferred (notification-lifecycle piece done; full shell extraction outstanding).
