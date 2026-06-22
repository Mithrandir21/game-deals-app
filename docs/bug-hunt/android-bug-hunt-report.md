# Android Bug Hunt — Report

_Game Deals (Kotlin Multiplatform + Compose Multiplatform — Android `:app` + iOS `:iosApp`)_

## Summary
- **Total findings: 12**
- **Critical: 1 · High: 0 · Medium: 2 · Low: 9**
- **Specialists run (6):** coroutine-and-flow-defects, lifecycle-leak-hunter, compose-correctness, main-thread-violations, resource-leaks, kmp-defects
- **Files scanned:** ~477 Kotlin files across ~25 modules (production source; tests excluded)

The codebase is in unusually good shape for its size: disciplined structured concurrency, lifecycle-aware Flow collection, symmetric Compose effects, off-main I/O via `withContext`, and clean resource handling. There is **one Critical** (an iOS-only crash) and **two Medium** issues worth scheduling; everything else is Low (hardening / rare edge cases).

## Quick-win table

| ID | Severity | Category | Location | Effort | Conf. | Title |
|---|---|---|---|---|---|---|
| BUG-001 | 🔴 Critical | KMP / missing iOS DI binding | `DomainIosModule.kt:13` → `AccountModule.kt:27` | Small | High | `NotificationScheduler` unbound on iOS → Account tab crashes |
| BUG-002 | 🟠 Medium | Main-thread (CPU parse on Main) | `GamesRepository.kt:170,186` + 3 repos | Small | High | Cached-blob JSON decode runs on the main thread |
| BUG-003 | 🟠 Medium | Race (lost update) | `FollowedFranchiseRepository.kt:48-64` | Small | High | Non-atomic whole-list read-modify-write drops follows |
| BUG-004 | 🟡 Low | Main-thread (redundant) | `GamesRepository.kt:186` | Trivial | High | Price-history decode runs on Main even when discarded |
| BUG-005 | 🟡 Low | Compose (lifecycle-unaware collect) | `NavGraph.kt:73`; `MainViewController.kt:169` | Trivial | High | App-root search uses `collectAsState()` vs convention |
| BUG-006 | 🟡 Low | KMP / latent iOS crash | `NotificationPoll.kt:20`; `AppModule.kt:22` | Small | High | `NotificationPresenter` Android-only (latent iOS crash) |
| BUG-007 | 🟡 Low | Race (redundant fetch) | `NotesRepository.kt:96-100` | Small | High | `ensureLoaded` check-then-act → duplicate `/notes` fetch |
| BUG-008 | 🟡 Low | Compose (`rememberSaveable` clobbered) | `AppShellScaffold.kt:88,93-95` | Small | Medium | Unsubmitted search text overwritten on restore |
| BUG-009 | 🟡 Low | Race (stale result) | `DiscoverResultsViewModel.kt:112-133`; `DealsViewModel.kt:255-259` | Small | Medium | Untracked first-page `retry()` loads can race |
| BUG-010 | 🟡 Low | Lifecycle (static mutable hand-off) | `AuthRedirectBus.kt:13` | Trivial | Low | Unguarded static `CompletableDeferred` re-entrancy |
| BUG-011 | 🟡 Low | Lifecycle (event timing) | `NotificationRouteBus.kt:22` | Small | Low | Notification route may replay before collector attaches |
| BUG-012 | 🟡 Low | Race (stale emission) | `GamePeekController.kt:40-68` | Small | Low | `cancel()` without `join()` → stale peek emission |

---

## Findings (full detail)

### BUG-001: `NotificationScheduler` has no iOS binding — Account tab crashes on iOS

| Field | Value |
|---|---|
| **Severity** | Critical |
| **Category** | KMP defect — missing `actual`/DI binding (platform divergence) |
| **Location** | `domain/src/commonMain/.../scheduling/NotificationScheduler.kt:9` (interface); `domain/src/iosMain/.../di/DomainIosModule.kt:13` (binding absent); crash path `feature/account/.../di/AccountModule.kt:27` → `AccountScreen.kt:409,295` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `NotificationScheduler` is a `commonMain` interface bound only on Android (`DomainAndroidModule.kt:17`). `domainIosModule` binds only the Room builder — no iOS scheduler implementation or binding exists. The Account hub renders `NotificationDeliveryRow()` unconditionally (`AccountScreen.kt:295`), which resolves `koinViewModel<NotificationSettingsViewModel>()` (`AccountScreen.kt:409`), instantiated via `viewModel { NotificationSettingsViewModel(get(), get()) }` (`AccountModule.kt:27`) where the second `get()` is the missing `NotificationScheduler`.

**Impact.** Koin resolves constructor args eagerly, so the iOS Account tab — a top-level bottom-nav tab (`iosApp/.../MainViewController.kt:223`) — crashes with `NoDefinitionFoundException` the moment it composes. Android is unaffected. The intent was documented but never completed: `DomainModule.kt:120-121` says "Scheduler is platform-bound (domainAndroidModule / domainIosModule)."

**Evidence.**
```kotlin
// DomainIosModule.kt — binds Room builder only; no NotificationScheduler
// AccountModule.kt:27
viewModel { NotificationSettingsViewModel(get(), get()) }  // 2nd get() = NotificationScheduler → unresolved on iOS
// AccountScreen.kt:295 renders NotificationDeliveryRow() unconditionally
```

**Recommended fix.** Add an iOS `NotificationScheduler` binding to `domainIosModule` — a no-op implementation is acceptable if `BGTaskScheduler` isn't wired yet, so the in-app toggle stays functional and the tab doesn't crash. Add an iOS Koin `verify`/instantiation test to catch missing platform bindings at build time.

**Confidence rationale.** High — the missing binding, eager Koin constructor resolution, and unconditional render path are all confirmed by source. Only uncertainty is whether iOS QA already worked around it; code as-is crashes.

---

### BUG-002: Cached-blob JSON deserialization runs on the main thread (multiple repositories)

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Main-thread violation (CPU-bound JSON parse on Main) |
| **Location** | `domain/src/commonMain/.../repositories/games/GamesRepository.kt:170,186`; `.../deals/DealsRepository.kt:128`; `.../bundles/BundlesRepository.kt:74`; `.../stats/StatsRepository.kt:87` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** Read-through caches store the network payload as serialized JSON in Room and, on a cache hit, `decodeFromString` it back into models. These `suspend` repository methods contain no `withContext`; the decode runs on the calling coroutine's dispatcher. The DB read is off-main (Room `setQueryCoroutineContext`), but a suspend DAO call resumes the caller on the caller's dispatcher, so the decode runs on Main. The only callers are feature ViewModels on `viewModelScope` (Main) with no `flowOn`/`withContext` (grep across `feature/` returns none).

**Impact.** CPU JSON parse on the UI thread on every warm-cache read: Game Page open, deal click-through, Stats rankings, Bundles list. A few ms for small payloads; tens of ms of first-frame jank for a long price-history series or large list. Silent — doesn't throw, and StrictMode won't catch it (the disk read already happened off-thread).

**Evidence.**
```kotlin
val cached = gameDetailsCacheDao.get(gameId, country) ?: error(...)   // resumes on caller dispatcher
return json.decodeFromString(GameDetails.serializer(), cached.json)   // CPU parse on Main (GamesRepository.kt:170)
```

**Recommended fix.** Wrap the decode (and the refresh-path encode) in `withContext(Dispatchers.Default)` in the repositories — `Default` because it's pure CPU. Per-call-site `withContext` is more robust than ViewModel `flowOn` because some callers (`onRegionsSelected`, `onCandidatePicked`) call the repo directly inside `viewModelScope.launch`, not via a `flow {}`.

**Confidence rationale.** High that it runs on Main (no `withContext`; suspend-resume semantics; all callers on Main). Medium severity (not High) because payloads are single objects / modest lists, not the >100KB that would mean ANR — real-data magnitude is the only uncertain dimension.

---

### BUG-003: `FollowedFranchiseRepository.toggle` / `remove` is a non-atomic read-modify-write — lost updates

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Race condition (lost update) |
| **Location** | `domain/src/commonMain/.../repositories/franchise/FollowedFranchiseRepository.kt:48-64` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `toggle` and `remove` each read the **whole** followed-franchise list via `getFollowed()`, mutate it in memory, then `persist()` to both the `MutableStateFlow` and `Storage`. No `Mutex` serializes this, and there are suspension points between the read and the write. Two concurrent calls each read the same baseline and the last `persist()` wins, dropping the other's change.

**Impact.** Following/unfollowing two series in quick succession (each handler is a separate `viewModelScope.launch`, so they interleave) can silently lose one change. The persisted `Storage` drifts from user intent. The per-id Waitlist/Collection/Ignored repos mutate Room by a single id and are immune; this repo rewrites the entire list.

**Evidence.**
```kotlin
override suspend fun toggle(franchiseId: Long, name: String) {
    val current = getFollowed()                     // read whole list (suspends)
    val next = if (current.any { it.franchiseId == franchiseId }) current.filterNot { it.franchiseId == franchiseId }
               else current + FollowedFranchise(franchiseId, name, clock.nowMillis())
    persist(next)                                   // write whole list (suspends) — last writer wins
}
```

**Recommended fix.** Serialize the read-modify-write under a `Mutex` (shared by `toggle`, `remove`, and the `load`-seeding path); keep `storage.save` inside the critical section.

**Confidence rationale.** The non-atomic whole-list rewrite with intervening suspension points is plain in source. Medium severity reflects that it needs two near-simultaneous mutations (a fast double-tap on two different toggles).

---

### BUG-004: Price-history decode is unconditional — runs on Main even when discarded

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Main-thread violation (redundant CPU work on Main) |
| **Location** | `domain/src/commonMain/.../repositories/games/GamesRepository.kt:186` |
| **Effort** | Trivial |
| **Confidence** | High |

**Description.** `getPriceHistory` decodes the cached blob into `cachedHistory` at the top of the method, before the freshness check. On a fresh refresh the value is recomputed from the network top-up; the eager decode still ran (on Main) and may be thrown away.

**Impact.** A guaranteed main-thread JSON parse on every Game Page open with cached price history, including the "cache fresh, nothing to do" case. Subsumed by the BUG-002 fix; flagged separately for the redundant placement.

**Evidence.**
```kotlin
val cachedEntry = priceHistoryCacheDao.get(gameId, country)
val cachedHistory = cachedEntry?.let { json.decodeFromString(PriceHistory.serializer(), it.json) }  // :186 on Main, eager
```

**Recommended fix.** Move the decode into `withContext(Dispatchers.Default)` (BUG-002); ideally defer it until after `refreshIfNeeded()`. (The `since` cursor still needs the decoded points, so at minimum get it off Main.)

**Confidence rationale.** High on placement and Main-thread execution; Low severity — same parse as BUG-002, just slightly redundant, bounded to one screen.

---

### BUG-005: App-root search query collected with `collectAsState()` instead of `collectAsStateWithLifecycle()`

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Compose correctness — lifecycle-unaware Flow collection |
| **Location** | `app/src/main/.../navigation/NavGraph.kt:73`; `iosApp/.../MainViewController.kt:169` |
| **Effort** | Trivial |
| **Confidence** | High (deviation) / Low (runtime impact) |

**Description.** Both app roots read `SearchController.activeQuery` with plain `collectAsState()`. Every other screen (20+ sites) uses `collectAsStateWithLifecycle()`. These two are the only plain `collectAsState()` usages in production. _(Unified: the compose specialist flagged this as a consistency deviation; the coroutine specialist independently assessed the same two sites and judged them acceptable — the upstream is in-memory state and the host is always composed. Both agree the runtime risk is negligible.)_

**Impact.** Negligible — the root is composed for the app's entire foreground lifetime, and the upstream is a trivial in-memory `MutableStateFlow` (no Room invalidation/socket/network). Downside is convention inconsistency only.

**Evidence.**
```kotlin
val activeSearchQuery by SearchController.activeQuery.collectAsState()              // NavGraph.kt:73
val activeSearch by SearchController.activeQuery.collectAsStateWithLifecycle()     // convention, DealsScreen.kt:190
```

**Recommended fix.** Use `collectAsStateWithLifecycle()` (artifact already on classpath).

**Confidence rationale.** Deviation grep-confirmed; runtime impact genuinely Low because host is always composed and upstream is cheap. Hardening, not a bug fix.

---

### BUG-006: `NotificationPresenter` is Android-only — latent iOS crash when background delivery is wired

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | KMP defect — missing iOS binding (latent) |
| **Location** | `app/.../di/AppModule.kt:22` (Android binding); `domain/src/commonMain/.../scheduling/NotificationPoll.kt:20` (consumer) |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `NotificationPresenter` is bound only on Android, required by `runNotificationPoll` (`NotificationPoll.kt:20`). Currently unreachable on iOS — its only caller is `NotificationPollWorker` in `androidMain`, itself gated behind the missing scheduler (BUG-001).

**Impact.** No crash today (unreachable on iOS), but becomes a crash the instant iOS background delivery is wired. Should be completed together with BUG-001.

**Recommended fix.** Provide an iOS `NotificationPresenter` binding alongside the BUG-001 scheduler fix.

**Confidence rationale.** High on the missing binding; Low severity because it is not currently reachable on iOS.

---

### BUG-007: `NotesRepository.ensureLoaded` check-then-act races into a duplicate network fetch

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Race condition (redundant work) |
| **Location** | `domain/src/commonMain/.../repositories/notes/NotesRepository.kt:96-100` (called from `:55`) |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `ensureLoaded()` does a check-then-act on `notes.value` (`if (notes.value != null) return` then assign), invoked from `notes.onStart { ensureLoaded() }`. Two collectors subscribing at once can both pass the null check before either assigns, both firing `accountSource.getNotes()`.

**Impact.** A duplicate ITAD `/notes` round-trip on first load; the second response overwrites the first. Idempotent (same data), so no corruption — just a wasted call and possible brief flicker. Bounded to once per session/login.

**Evidence.**
```kotlin
private suspend fun ensureLoaded() {
    if (notes.value != null) return                          // check
    notes.value = runCatching { accountSource.getNotes() }   // act — two collectors both reach here
        .getOrElse { emptyList() }.associate { it.gameId to it.note }
}
```

**Recommended fix.** Guard with a `Mutex` (re-check inside the lock), or coalesce through a single shared `Deferred` / the project's `RequestCoalescer`.

**Confidence rationale.** Check-then-act across a suspension point is unambiguous. Low severity because the fetch is idempotent.

---

### BUG-008: In-progress (unsubmitted) search text is overwritten on state restoration

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Compose correctness — `rememberSaveable` value clobbered by an effect |
| **Location** | `common/ui/src/commonMain/.../shell/AppShellScaffold.kt:88,93-95` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** `searchText` is hoisted into `rememberSaveable` (survives process death), but `LaunchedEffect(showSearchField, activeSearchQuery)` unconditionally rewrites it to `activeSearchQuery.orEmpty()` whenever the field is shown, so the saved value is never honored.

**Impact.** Minor UX edge case. Typed-but-unsubmitted text is replaced by the last submitted query on restore / effect re-entry; the `rememberSaveable` does no useful work.

**Evidence.**
```kotlin
var searchText by rememberSaveable { mutableStateOf("") }              // :88 — saved...
LaunchedEffect(showSearchField, activeSearchQuery) {                   // :93
    if (showSearchField) searchText = activeSearchQuery.orEmpty()      // :94 — ...but always overwritten
}
```

**Recommended fix.** Drop to plain `remember` (value never honored anyway), or seed only when `activeSearchQuery` actually changes: `LaunchedEffect(activeSearchQuery) { if (activeSearchQuery != null) searchText = activeSearchQuery }`.

**Confidence rationale.** Medium — overwrite is clear; seeding-on-open may be intentional. The defect is that `rememberSaveable` implies an intent the effect defeats. No stability consequence.

---

### BUG-009: `DealsViewModel.retry()` / `DiscoverResultsViewModel.loadFirstPage()` launch untracked first-page loads that can race

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Race condition (stale result) |
| **Location** | `feature/discover/.../DiscoverResultsViewModel.kt:112-133`; `feature/deals/.../DealsViewModel.kt:255-259` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** Both VMs cancel the load-more `appendJob` before a first-page load but do not assign the first-page coroutine to any tracked `Job`. Each does a bare `viewModelScope.launch { ... }` writing the final result to `uiState`. Two rapid invocations run concurrently with no mutual cancellation; whichever completes last wins, possibly the older request → stale results.

**Impact.** Rare, low-impact: a double-tapped "Retry" can briefly show superseded results. No crash/leak; self-corrects. The browse-filter path in `DealsViewModel` is already protected by `collectLatest` (`:165-176`); this gap is only the imperative entry points.

**Evidence.**
```kotlin
fun loadFirstPage() {
    appendJob?.cancel()                       // cancels load-more, not a prior first-page load
    viewModelScope.launch { ... uiState.update { ... } }   // untracked — two can race
}
```

**Recommended fix.** Track the first-page job and cancel it on re-entry, or route retries through a trigger flow consumed with `collectLatest`/`flatMapLatest` (the pattern `GamePageViewModel.reloadTrigger` already uses).

**Confidence rationale.** Untracked launch is clear. Medium because it needs two near-simultaneous `retry()` calls.

---

### BUG-010: `AuthRedirectBus` static `CompletableDeferred` overwritten without a re-entrancy guard

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Lifecycle — process-global mutable hand-off |
| **Location** | `AuthRedirectBus.kt:13` |
| **Effort** | Trivial |
| **Confidence** | Low |

**Description.** `AuthRedirectBus` is a process-global `object` whose static `CompletableDeferred` is overwritten on each `authorize()` without cancelling the prior one. Currently safe because its only caller clears in a `finally`, but fragile if a concurrent `authorize()` is ever added.

**Impact.** No leak today (payload holds no Context). A future concurrent caller could orphan a deferred and hang an OAuth continuation. Latent.

**Recommended fix.** `pending?.cancel()` before reassigning, or model the hand-off as a `Channel`/`SharedFlow`.

**Confidence rationale.** Low — not currently reachable as a bug; flagged as a hardening item against foreseeable change.

---

### BUG-011: Notification tap route may replay before the nav collector is attached

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Lifecycle — event timing edge |
| **Location** | `NotificationRouteBus.kt:22` |
| **Effort** | Small |
| **Confidence** | Low |

**Description.** `NotificationRouteBus` buffers a tapped-notification route in a `Channel` that can be replayed at an unexpected time if the tap lands during the onboarding / pre-composition window where `NavGraph`'s collector isn't yet attached.

**Impact.** A behavioural timing edge: a notification tapped during cold start / onboarding could route the user at an unexpected moment (or after onboarding completes). No crash/leak.

**Recommended fix.** Gate consumption on the nav host being ready, or drop/replace stale routes once the destination context changes.

**Confidence rationale.** Low — depends on a narrow timing window the analyzer cannot fully observe; flagged for human judgment.

---

### BUG-012: `GamePeekController` cancels the previous load without joining — stale emission can land on the shared `data` flow

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Race condition (stale emission) |
| **Location** | `common/ui/src/commonMain/.../deal/GamePeekController.kt:40-46, 53-68, 121-124` |
| **Effort** | Small |
| **Confidence** | Low |

**Description.** `load`, `loadByTitle`, and `dismiss` each do `loadJob?.cancel()` then launch a new coroutine that emits into the single shared `data: MutableStateFlow<GamePeekSheetData?>`. `cancel()` is asynchronous and not followed by `join()`, so the previous coroutine may still reach a terminal `emit` after the new one emitted `Loading`.

**Impact.** Edge-case visual glitch only: rapidly peeking game A then B could flash A's content/error in B's sheet for a frame. No crash/leak; the window is narrow because suspending repo calls give a prompt cancellation point.

**Evidence.**
```kotlin
fun load(scope: CoroutineScope, gameId: String, gameName: String, thumb: String?) {
    loadJob?.cancel()                       // async cancel, no join
    loadJob = scope.launch { data.emit(Loading(...)); emitGameDetails(...) }   // old job may still emit
}
```

**Recommended fix.** Use `cancelAndJoin()` before relaunching, or model the peek as `flatMapLatest` over a `gameId` key flow (mirroring `GamePageViewModel.reloadTrigger.flatMapLatest`).

**Confidence rationale.** Low — real in principle, but the suspending calls almost always provide a cancellation point before the stale `emit`, so the stale value rarely wins.

---

## Specialists that found nothing
- **android-bug-hunting-resource-leaks** — 0 findings. All persistence goes through Room; all networking through Ktor unary `.body<T>()`. No manually opened streams/cursors, no per-request HttpClients, WebView disposed fully in `onRelease`. Clean.

## Notes and limitations
- **Test sources not scanned** — `*Test`, `*androidDeviceTest`, and the `:testing` module were excluded by design to keep signal high. Defects reachable only from test harnesses are out of scope.
- **Native/iOS runtime not executed** — KMP findings are from static comparison of `commonMain` vs `androidMain`/`iosMain`. BUG-001 (iOS Account-tab crash) is high-confidence from source but was not reproduced on a device/simulator; an iOS Koin `verify` test would confirm it deterministically.
- **Real-data magnitude unknown for BUG-002** — severity hinges on payload sizes in production. If price-history / deal-list JSON can grow large, BUG-002 escalates toward High (ANR-adjacent jank).
- **Low-confidence items needing human judgment:** BUG-010, BUG-011, BUG-012 are latent / timing-window edges flagged for awareness rather than confirmed user-visible defects.
- **Dead code noticed (not a defect):** `RequestCoalescer` (`domain/.../cache/RequestCoalescer.kt`) is implemented and documented but not instantiated in production — a ready-made fix vehicle for BUG-007.
