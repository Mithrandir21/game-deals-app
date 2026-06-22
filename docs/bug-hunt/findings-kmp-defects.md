# KMP Defects — Bug Hunt Findings

Scope: `commonMain` expectations vs `androidMain`/`iosMain` actuals across all modules (tests excluded). **2 findings — 1 Critical, 0 High, 0 Medium, 1 Low.**

**Verified clean:** all 11 `expect` declarations have matching Android + iOS actuals with consistent signatures; `commonMain` has zero `android.*`/`java.*`/`javax.*` import leakage; no `java.time`/`SimpleDateFormat`/`runBlocking`/`Dispatchers.Main`/`android.util.Log` in common; date/time, storage, threading, and WebView interop are correctly bridged per platform. No `iosX64` target, so no Intel-simulator divergence concern. The RegionDetector null-vs-blank difference is benign (consumer treats null/blank identically; `:feature:onboarding` is not an iOS dependency).

### BUG-001: `NotificationScheduler` has no iOS binding — Account tab crashes on iOS

| Field | Value |
|---|---|
| **Severity** | Critical |
| **Category** | KMP defect — missing `actual`/DI binding (platform divergence) |
| **Location** | `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/scheduling/NotificationScheduler.kt:9` (interface); `domain/src/iosMain/kotlin/pm/bam/gamedeals/domain/di/DomainIosModule.kt:13` (binding absent); crash path `feature/account/src/commonMain/kotlin/pm/bam/gamedeals/feature/account/di/AccountModule.kt:27` → `AccountScreen.kt:409,295` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `NotificationScheduler` is a `commonMain` interface bound only on Android (`DomainAndroidModule.kt:17`). `domainIosModule` (`DomainIosModule.kt:13`) binds only the Room builder — no iOS scheduler implementation or binding exists anywhere. The Account hub renders `NotificationDeliveryRow()` unconditionally (`AccountScreen.kt:295`), which resolves `koinViewModel<NotificationSettingsViewModel>()` (`AccountScreen.kt:409`); Koin instantiates it via `viewModel { NotificationSettingsViewModel(get(), get()) }` (`AccountModule.kt:27`) where the second `get()` is the missing `NotificationScheduler`.

**Impact.** Because Koin resolves constructor args eagerly, the iOS Account tab — a top-level bottom-nav tab (`iosApp/.../MainViewController.kt:223`) — crashes with `NoDefinitionFoundException` the moment it composes. Android is unaffected. The intent was documented but never completed: `DomainModule.kt:120-121` says "Scheduler is platform-bound (domainAndroidModule / domainIosModule)."

**Evidence.**
```kotlin
// DomainIosModule.kt — binds Room builder only; no NotificationScheduler
// AccountModule.kt:27
viewModel { NotificationSettingsViewModel(get(), get()) }  // 2nd get() = NotificationScheduler → unresolved on iOS
// AccountScreen.kt:295 renders NotificationDeliveryRow() unconditionally → koinViewModel<NotificationSettingsViewModel>()
```

**Recommended fix.** Add an iOS `NotificationScheduler` binding to `domainIosModule` — a no-op implementation is acceptable if `BGTaskScheduler` isn't wired yet, so the in-app toggle stays functional and the tab doesn't crash. Add an iOS Koin `verify`/instantiation test to catch missing platform bindings at build time.

**Confidence rationale.** High — the missing binding, eager Koin constructor resolution, and unconditional render path are all confirmed by source. Only uncertainty is whether iOS QA already hit/worked around it; code as-is crashes.

### BUG-002: `NotificationPresenter` is Android-only — latent iOS crash when background delivery is wired

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | KMP defect — missing iOS binding (latent) |
| **Location** | `app/.../di/AppModule.kt:22` (Android binding); `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/scheduling/NotificationPoll.kt:20` (consumer) |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `NotificationPresenter` is likewise bound only on Android, required by `runNotificationPoll` (`NotificationPoll.kt:20`). Currently unreachable on iOS — its only caller is `NotificationPollWorker` in `androidMain`, itself gated behind the missing scheduler (BUG-001).

**Impact.** No crash today (unreachable on iOS), but becomes a crash the instant iOS background delivery is wired. Should be completed together with BUG-001.

**Evidence.**
```kotlin
// NotificationPoll.kt:20 — runNotificationPoll depends on NotificationPresenter (commonMain)
// Bound only in app/.../di/AppModule.kt:22 (Android); no iosMain binding
```

**Recommended fix.** Provide an iOS `NotificationPresenter` binding alongside the BUG-001 scheduler fix.

**Confidence rationale.** High on the missing binding; Low severity because it is not currently reachable on iOS.
