# Android Bug Hunt — Report

**Branch:** `feature/kmp-migration` · **HEAD:** `1a183e5` · **Date:** 2026-05-06

## Summary

- **Total findings:** 10
- **Critical:** 0 · **High:** 1 · **Medium:** 2 · **Low:** 7
- **Specialists run:** coroutine-and-flow-defects, compose-correctness, kmp-defects, main-thread-violations, resource-leaks, lifecycle-leak-hunter
- **Files scanned:** 161 production `.kt` files (commonMain 124, androidMain 14, iosMain 12, legacy `main` 11)

The codebase is in genuinely good shape. Most playbook antipatterns have been internalized as project lessons (`SingleEventEffect`+`rememberUpdatedState`, `collectAsStateWithLifecycle` everywhere, `ImmutableList`+`@Immutable`, `LocalActivity.current`, `AndroidView.onRelease`, `_uiState.asStateFlow()`, dispatcher injection from each platform's Koin module). The single High-severity finding is the one missed application of an existing lesson.

## Quick-win table

| ID | Severity | Category | Location | Effort | Confidence | Title |
|---|---|---|---|---|---|---|
| BUG-001 | High | Cancellation swallow | `feature/giveaways/.../GiveawaysViewModel.kt:66-76` | Trivial | High | `reloadGiveaways` swallows `CancellationException` via bare `catch (_: Throwable)` |
| BUG-002 | Medium | Startup blocking | `domain/.../DomainModule.kt:27-40` (resolved from `feature/home/.../HomeScreen.kt:86`) | Small | Medium | Lazy Koin first-access opens SQLite on Main during first composition |
| BUG-003 | Medium | Flow tombstone | `feature/store/.../StoreViewModel.kt:63-74` | Trivial | Medium | `StoreViewModel.deals` has no `.catch`; one refresh failure permanently empties the StateFlow |
| BUG-004 | Low | Missing `remember` | `feature/game/.../GameScreen.kt:301`, `feature/store/.../StoreScreen.kt:219` | Trivial | Medium | `TopAppBarDefaults.pinnedScrollBehavior(...)` reallocated on every recomposition |
| BUG-005 | Low | Latent threading | `common/.../iosMain/.../PlatformDateFormatter.ios.kt:13-20` | Trivial | Low | Shared `NSDateFormatter` at module scope on iOS — safe today, foot-gun if reconfigured |
| BUG-006 | Low | Latent threading | `logging/.../LoggerImpl.kt:3-41` | Trivial | Low | `LoggerImpl.loggers` is an unsynchronized `MutableSet` with public `add/remove` mutators |
| BUG-007 | Low | Contract fragility | `common/.../androidMain/.../SharedPreferencesBackend.kt:11-18` | Trivial | Low | `commit()` thread contract carried only in a code comment |
| BUG-008 | Low | Wasted work | `domain/.../androidMain/.../DomainAndroidModule.kt:19-22` | Trivial | Low | Room `setQueryCallback` allocates per-query strings unconditionally in release |
| BUG-009 | Low | Latent RMW race | `feature/search/.../SearchViewModel.kt:67-85` | Trivial | Low | `searchGames` per-field merge against `replayCache` is racy by design |
| BUG-010 | Low | Latent state drift | `feature/search/.../SearchScreen.kt:352-354` | Small | Low | `Filters` `rememberSaveable` initial-value not re-keyed on parent change |

## Findings (full detail)

### BUG-001: `reloadGiveaways` swallows `CancellationException` via bare `catch (_: Throwable)`

| Field | Value |
|---|---|
| Severity | High |
| Category | Cancellation swallow / non-cooperative cancellation |
| Location | `feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:66-76` |
| Effort | Trivial |
| Confidence | High |

**Description.** `reloadGiveaways` launches a coroutine that calls the suspending `giveawaysRepository.refreshGiveaways()` and wraps it in `catch (_: Throwable)`, which also matches `CancellationException`. This is the exact antipattern called out in lesson `L-2026-05-02-04`. All other `catch (Throwable)` blocks in the codebase that wrap suspending work (DealsApi, GamesApi, StoresApi, ReleaseApi, gamerpower GamesApi, DealDetailsController) already rethrow `CancellationException` first; this is the only outlier.

**Impact.** When the ViewModel is cleared (or any ancestor scope is cancelled) while `refreshGiveaways()` is suspended, the `CancellationException` is silently dropped. The catch body then writes `refreshOutcomeFlow.value = RefreshOutcome.Error`, so a cancelled reload looks like a refresh failure. Combined with `loadingFlow.value = true` set above (only cleared by a subsequent Room emission), the surviving StateFlow can retain an `Error` reading set by a cancelled reload.

**Evidence.**
```kotlin
fun reloadGiveaways() {
    viewModelScope.launch {
        loadingFlow.value = true
        refreshOutcomeFlow.value = RefreshOutcome.Idle
        try {
            giveawaysRepository.refreshGiveaways()
        } catch (_: Throwable) {                              // swallows CE
            refreshOutcomeFlow.value = RefreshOutcome.Error
        }
    }
}
```

**Recommended fix.** Catch `CancellationException` explicitly first and rethrow:
```kotlin
try {
    giveawaysRepository.refreshGiveaways()
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    refreshOutcomeFlow.value = RefreshOutcome.Error
}
```

**Confidence rationale.** High — exact antipattern with a confirmed lesson; call site unambiguously wraps a `suspend` function. Trivial single-line fix.

---

### BUG-002: Lazy Koin first-access opens SQLite on Main during first composition

| Field | Value |
|---|---|
| Severity | Medium |
| Category | Startup main-thread blocking |
| Location | `domain/src/commonMain/.../domain/di/DomainModule.kt:27-40`, `domain/src/androidMain/.../domain/di/DomainAndroidModule.kt:13-23`, triggered from `feature/home/.../HomeScreen.kt:86` |
| Effort | Small |
| Confidence | Medium |

**Description.** `DomainDatabase` is a Koin `single` whose factory calls `.build()` on a Room `RoomDatabase.Builder`. Each DAO `single` calls `get<DomainDatabase>().getXxxDao()` — Koin resolves these lazily on first request. The first DAO request happens at `HomeScreen.kt:86` via `viewModel: HomeViewModel = koinViewModel()`, which runs on the **main thread** during composition. `HomeViewModel`'s constructor pulls all five repositories (HomeViewModel.kt:51-58), so Koin transitively resolves `DomainDatabase`. `Room.databaseBuilder(...).build()` performs synchronous file I/O (opens / creates the SQLite file, runs migrations, applies type converters) on the calling thread.

**Impact.** Cold start on a fresh install creates the SQLite file on Main — typically tens of ms. App update with schema bump (current DB version is `4`, with `fallbackToDestructiveMigration(dropAllTables = true)`): destructive migration drops and recreates every table synchronously on Main during the first frame. ANR-class on low-end devices with large databases. Path: `MainActivity.onCreate` → `setContent { NavGraph() }` → `HomeScreen` recomposes → `koinViewModel()` resolves → `DomainDatabase.build()` → SQLite open on Main.

**Evidence.**
```kotlin
// DomainModule.kt
single<DomainDatabase> {
    get<RoomDatabase.Builder<DomainDatabase>>()
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addTypeConverter(get<StoreImagesConverter>())
        ...
        .build()  // synchronous: opens/creates SQLite on caller thread
}
single { get<DomainDatabase>().getDealsDao() }  // first DAO get triggers .build()
```

**Recommended fix.** Move the database open off Main. Cheapest path: in `GameDealsApplication.onCreate`, after `startKoin { ... }`, kick off `CoroutineScope(Dispatchers.IO).launch { val db: DomainDatabase = getKoin().get(); db.openHelper.writableDatabase }` so the SQLite open happens on a background thread before the first frame composes. Cleaner alternatives: `androidx.startup` background `Initializer`, or a suspend `databaseProvider` that opens inside `withContext(Dispatchers.IO)`.

**Confidence rationale.** Medium because the *direction* is unambiguous (Koin singles resolve on the requesting thread; Compose composition runs on Main; Room `.build()` is documented synchronous), but *magnitude* is device- and DB-state-dependent. StrictMode (currently `detectAll().penaltyLog()` per L-2026-05-01-08) should already log this as `StrictModeDiskReadViolation` on cold start in debug — confirm size before fixing.

---

### BUG-003: `StoreViewModel.deals` has no `.catch`; one refresh failure permanently empties the StateFlow

| Field | Value |
|---|---|
| Severity | Medium |
| Category | Flow tombstone / unrecoverable error state |
| Location | `feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt:63-74` |
| Effort | Trivial |
| Confidence | Medium |

**Description.** `StoreViewModel.deals` is built as `storeIdFlow → flatMapLatest { dealsRepository.observeStoreDeals(it) } → map → logFlow → stateIn(WhileSubscribed(5000), persistentListOf())`. The upstream `observeStoreDeals` does `dealsDao.observeStoreDeals(storeId).onStart { refreshDeals(storeId) }` (DealsRepository.kt:49-51), so a network failure inside `refreshDeals` propagates up the Flow before any DB emission. The chain has no `.catch` (compare to the sibling `uiState`, which does), so the `stateIn` upstream collector terminates exceptionally.

**Impact.** While at least one subscriber is active, the upstream is alive. When `refreshDeals` throws (no network, server 5xx mapped through `RemoteHttpException`), the upstream completes exceptionally; the `stateIn` collector dies; existing subscribers never see new deals. The user keeps seeing `persistentListOf()` even after connectivity returns. Recovery only happens if all subscribers leave for ≥5 s and a new subscriber arrives, re-triggering the upstream. The sibling `uiState` *does* have `.catch { emit(StoreScreenData.Error) }`, so the screen header shows an error — but the deals list rendered from `viewModel.deals` (StoreScreen.kt:78) silently stays empty when the user hits "retry".

**Evidence.**
```kotlin
val deals: StateFlow<ImmutableList<Deal>> = storeIdFlow
    .filterNotNull()
    .distinctUntilChanged()
    .flatMapLatest { dealsRepository.observeStoreDeals(it) }
    .map { it.toImmutableList() }
    .logFlow(logger)             // re-throws via .onError
    .stateIn(...)                // no `.catch` upstream — collector dies on first throw
```

**Recommended fix.** Add `.catch { emit(persistentListOf()) }` immediately above `stateIn`:
```kotlin
.map { it.toImmutableList() }
.logFlow(logger)
.catch { emit(persistentListOf()) }
.stateIn(...)
```

**Confidence rationale.** Medium — the failure mode requires the network refresh to throw before any DB emission; pre-existing cache may mask this on a warm cache. Asymmetry with the sibling `uiState` flow (which catches) and with `StoreScreen`'s retry UX (which recovers `uiState` without re-priming `deals`) make this a real regression vector. Not High because the end-to-end exception path through Ktor + sandwich + RemoteExceptionTransformer wasn't traced.

---

### BUG-004: `TopAppBarDefaults.pinnedScrollBehavior(...)` reallocated on every recomposition

| Field | Value |
|---|---|
| Severity | Low |
| Category | Compose — missing `remember` on stateful resource |
| Location | `feature/game/src/commonMain/.../GameScreen.kt:301`, `feature/store/src/commonMain/.../StoreScreen.kt:219` |
| Effort | Trivial |
| Confidence | Medium |

**Description.** Both `GameScreen.ScreenScaffold` and `StoreScreen.StoreToolbar` build their scroll behavior with `TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())` directly in the composable body, with no surrounding `remember`. The inner `TopAppBarState` is preserved (via `rememberTopAppBarState()`), so animation/offset state survives — but the outer behavior wrapper (and its `flingAnimationSpec`, `canScroll` predicate) is recreated and re-attached to the `TopAppBar` each frame.

**Impact.** No correctness bug observed today (`pinnedScrollBehavior` delegates almost everything to the inner state, which IS remembered). With richer behaviors (e.g., `enterAlwaysScrollBehavior`) the missing `remember` actually drops mid-animation state. Latent risk if the behavior is later swapped, plus minor allocation churn now.

**Evidence.**
```kotlin
// GameScreen.kt:299–302
private fun ScreenScaffold(...) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val currentOnRetry by rememberUpdatedState(onRetry)
```

**Recommended fix.**
```kotlin
val topAppBarState = rememberTopAppBarState()
val scrollBehavior = remember(topAppBarState) {
    TopAppBarDefaults.pinnedScrollBehavior(topAppBarState)
}
```

**Confidence rationale.** Medium because Material3's official samples wrap with `remember`. Low impact because `pinnedScrollBehavior` delegates to the inner `state` (which IS remembered). Could be safely demoted to "ignore" if zero recomposition cost has been measured here.

---

### BUG-005: Shared `NSDateFormatter` at module scope on iOS — safe today, foot-gun if reconfigured

| Field | Value |
|---|---|
| Severity | Low |
| Category | KMP / iOS concurrency |
| Location | `common/src/iosMain/kotlin/pm/bam/gamedeals/common/datetime/formatting/PlatformDateFormatter.ios.kt:13-20` |
| Effort | Trivial |
| Confidence | Low |

**Description.** `iosFormatter` is a top-level `private val` holding a single `NSDateFormatter` instance configured once via `apply { dateFormat = ...; locale = ...; timeZone = ... }`. Every call to `formatLocaleAwareDate(instant)` reuses the same object. The formatter is invoked from CheapShark response mappers (`DealMappers.kt:37/61/85`, `GameMappers.kt:41/58`) which run on whatever dispatcher Ktor's Darwin engine parks the response on.

**Impact.** Safe today by Apple's documented thread-safety contract for `NSDateFormatter` under `NSDateFormatterBehavior10_4` (default since iOS 7), as long as no caller reconfigures the formatter after init. The pattern becomes unsafe the moment someone adds locale switching at runtime or reuses the formatter for parsing. `NSDateFormatter` is a famous foot-gun — flagged so a future reconfiguring change is noticed.

**Evidence.**
```kotlin
private val iosFormatter = NSDateFormatter().apply {
    dateFormat = "MMM dd, yyyy"
    locale = NSLocale.currentLocale
    timeZone = NSTimeZone.systemTimeZone
}

internal actual fun formatLocaleAwareDate(instant: Instant): String =
    iosFormatter.stringFromDate(instant.toNSDate())
```

**Recommended fix.** Either (1) instantiate the formatter inside `formatLocaleAwareDate` (cheap on iOS for purely formatting use), or (2) add a comment documenting the thread-safety assumption so a future change doesn't quietly add a mutator.

**Confidence rationale.** Low because Apple's documented behavior says this is safe today. Flagged because it's the kind of code that becomes unsafe the moment someone adds locale switching or reusing for parsing.

---

### BUG-006: `LoggerImpl.loggers` is an unsynchronized `MutableSet` with public mutators

| Field | Value |
|---|---|
| Severity | Low |
| Category | Latent thread-safety |
| Location | `logging/src/commonMain/kotlin/pm/bam/gamedeals/logging/LoggerImpl.kt:3-41` |
| Effort | Trivial |
| Confidence | Low |

**Description.** `LoggerImpl` holds `private val loggers: MutableSet<LoggingInterface>` and exposes `addLoggerListener` / `removeLoggerListener` that mutate it without synchronization. `log` and `fatalThrowable` iterate the set on whatever thread called the logger.

**Impact.** No current call sites for `addLoggerListener` / `removeLoggerListener` — listeners are seeded once in the Koin module and never mutated. So this is purely latent today. The commit message for `42c57f4` ("When real Sentry lands the listener will be a new addition to the set, not a replacement of this stub") explicitly anticipates a future call site that adds a listener at iOS app launch. At that point, on Kotlin/Native (or JVM with the `LinkedHashSet` returned by `mutableSetOf()`), concurrent add/log produces `ConcurrentModificationException` or torn iteration.

**Evidence.**
```kotlin
internal class LoggerImpl(private val loggers: MutableSet<LoggingInterface>) : Logger {
    override fun log(level: LogLevel, tag: String?, throwable: Throwable?, messageProvider: () -> String) =
        loggers.filter { it.isEnabled() }.forEach { it.onLog(level, messageProvider(), tag, throwable) }

    override fun addLoggerListener(loggingInterface: LoggingInterface) { loggers.add(loggingInterface) }
    override fun removeLoggerListener(loggingInterface: LoggingInterface) { loggers.remove(loggingInterface) }
}
```

**Recommended fix.** Either (1) switch to `kotlinx.atomicfu.atomic` reference holding an immutable `Set` and copy-on-write in add/remove (cheap, KMP-correct), or (2) document that listener registration must happen during DI bootstrap before any logger consumer runs and consider removing the public `add/removeLoggerListener` API entirely.

**Confidence rationale.** Low because no caller currently mutates the set. Flagged now because the commit that dropped the iOS Sentry stub explicitly says real Sentry will land as an *addition* — at that point this latent issue activates.

---

### BUG-007: `KeyValueBackend.commit()` thread contract carried only in a code comment

| Field | Value |
|---|---|
| Severity | Low |
| Category | Contract fragility / latent main-thread risk |
| Location | `common/src/androidMain/kotlin/pm/bam/gamedeals/common/storage/SharedPreferencesBackend.kt:11-18` |
| Effort | Trivial |
| Confidence | Low |

**Description.** `SharedPreferencesBackend.writeString`/`remove` use `prefs.edit()…commit()`. The inline comment correctly notes that callers run this off-thread inside `withContext(IO)`, and the only caller (`StorageImpl`) does. So this is not a Main-thread violation today. Fragility: `KeyValueBackend` is `internal`, but if any future caller bypasses `StorageImpl` and uses the backend directly from Main, `commit()` will silently block. There is no compile-time enforcement and no `@WorkerThread` annotation indicating the contract.

**Impact.** Latent. No current Main-thread caller. Future regression risk only.

**Evidence.**
```kotlin
override fun writeString(key: String, value: String): Boolean =
    prefs.edit().putString(key, value).commit()
override fun remove(key: String): Boolean = prefs.edit().remove(key).commit()
```

**Recommended fix.** Annotate `KeyValueBackend` (or the Android impl) with `@WorkerThread` so Lint flags any Main-thread caller. Alternatively, switch to `apply()` and drop the `Boolean` return contract — only the test hook appears to inspect it.

**Confidence rationale.** Low because no current caller is on Main — purely contract-not-enforced risk.

---

### BUG-008: Room `setQueryCallback` allocates per-query strings unconditionally in release

| Field | Value |
|---|---|
| Severity | Low |
| Category | Wasted work / debug-only logic in release |
| Location | `domain/src/androidMain/kotlin/pm/bam/gamedeals/domain/di/DomainAndroidModule.kt:19-22` |
| Effort | Trivial |
| Confidence | Low |

**Description.** Room's `setQueryCallback` is wired to `Executors.newSingleThreadExecutor()` with no shutdown hook and no debug-build gate. The callback fires on every query and forwards through `verbose(logger) { … }`. The `Logger` filters disabled levels cheaply, but the per-query string interpolation `"SQL Query: $sqlQuery SQL Args: $bindArgs"` is the `messageProvider` lambda, and `LoggerImpl.log` invokes it unconditionally (`it.onLog(level, messageProvider(), ...)`) before the listener decides whether to emit. So the string is built per query in release even when no enabled `LoggingInterface` consumes verbose logs.

**Impact.** Wasted CPU per Room query in release builds. Unbounded executor lifetime. L-2026-05-01-08 already established the codebase's preference for `ApplicationInfo.FLAG_DEBUGGABLE`-gated debug-only logic.

**Evidence.**
```kotlin
.setQueryCallback(
    { sqlQuery, bindArgs -> verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" } },
    Executors.newSingleThreadExecutor()
)
```

**Recommended fix.** Wrap the `setQueryCallback` registration in `if (isDebuggable())`, mirroring the StrictMode gate in `GameDealsApplication.onCreate`.

**Confidence rationale.** Low — not a Main-thread bug, but wasted work + an established pattern violation.

---

### BUG-009: `SearchViewModel.searchGames` per-field merge against `replayCache` is racy by design

| Field | Value |
|---|---|
| Severity | Low |
| Category | Latent read-modify-write race |
| Location | `feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt:67-85` |
| Effort | Trivial |
| Confidence | Low |

**Description.** `searchGames` reads `searchParametersFlow.replayCache.firstOrNull()` and uses its fields as defaults (`title ?: current.title`, etc.) before re-emitting via `searchParametersFlow.emit(searchParameters)`. The launch wraps that as `viewModelScope.launch { val current = …; emit(...) }`. Two rapid invocations can both read `current` before either emits — analogous to the read-modify-write antipattern in `L-2026-05-02-01`.

**Impact.** Concurrent calls of the form `searchGames(title = "Foo")` then `searchGames(lowerPrice = 1000)` can race: both read the pre-merge value; the second emit overwrites the first's intended title with the prior (now stale) title. The flow is collected via `flatMapLatest`, so only the second value drives the search — the first's title contribution is dropped. The current single call site (SearchScreen.kt:106) supplies **all** parameters from `rememberSaveable` composable state, so the "keep prior" branch never runs in production. Latent against a future call site that forwards only one field at a time.

**Evidence.**
```kotlin
fun searchGames(title: String? = null, ...) {
    viewModelScope.launch {
        val current = searchParametersFlow.replayCache.firstOrNull() ?: SearchParameters()
        val searchParameters = SearchParameters(
            title = title ?: current.title,
            lowerPrice = lowerPrice ?: current.lowerPrice,
            ...
        )
        searchParametersFlow.emit(searchParameters)
    }
}
```

**Recommended fix.** Either (a) replace the SharedFlow with `MutableStateFlow<SearchParameters>` and use `.update { current -> SearchParameters(title = title ?: current.title, ...) }` (atomic by design), or (b) drop the per-field "keep prior" merge — require callers to supply the full snapshot.

**Confidence rationale.** Low — race is theoretically reachable but practically unreachable from the single existing call site. Recorded because the merge logic exists *for* the partial-fields case the API signature suggests.

---

### BUG-010: `Filters` `rememberSaveable` initial-value not re-keyed on parent change

| Field | Value |
|---|---|
| Severity | Low |
| Category | Latent state drift |
| Location | `feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt:352-354` |
| Effort | Small |
| Confidence | Low |

**Description.** Inside `Filters(existingSearchParameters: SearchParameters, …)`, three `rememberSaveable` slots derive their *initial* value from `existingSearchParameters`, but the parameter is not passed as a key. Once a slot is first composed, it persists the slider value across the `ModalBottomSheet`'s show→hide→show cycle. If a future code path mutates `existingSearchParameters` while the sheet is closed (e.g., a "Reset filters" affordance), reopening the sheet will *not* reflect the new initial.

**Impact.** No observable bug today — the only path that updates `existingSearchParameters` is the in-sheet sliders themselves. The bug only manifests if an external "Reset" or "Apply preset" feature is added.

**Evidence.**
```kotlin
var priceSliderValue by rememberSaveable(stateSaver = floatRangeSaver) { mutableStateOf(existingPriceRange) }
var steamSliderValue by rememberSaveable { mutableFloatStateOf(existingMin) }
var exactMatch by rememberSaveable { mutableStateOf(existingSearchParameters.exact ?: false) }
```

**Recommended fix.** Either accept current behavior (state-of-the-sheet persistence is the UX) and add a comment, or key the slot to invalidate on parent change:
```kotlin
var priceSliderValue by rememberSaveable(
    existingPriceRange,
    stateSaver = floatRangeSaver,
) { mutableStateOf(existingPriceRange) }
```

**Confidence rationale.** Low because the current behavior may be intentional UX. Flagged for product input rather than recommending a change unilaterally.

---

## Specialists that found nothing

- **resource-leaks** — Zero findings. A read-mostly KMP app fronted by Room (auto-managed cursors via `suspend`/`Flow` DAOs), Ktor with typed `body<T>()` consumption (auto-closing), and a Coil singleton with no caller-owned cache/client is a near-empty surface for resource leaks.
- **lifecycle-leak-hunter** — Zero findings. Single-Activity Compose Multiplatform app with no Fragments, Services, BroadcastReceivers, ContentProviders, no `Handler`/`postDelayed`, no `addObserver` outside Compose-lifecycle helpers, and Koin singletons that only capture the Application Context.

## Notes and limitations

- **Legacy `:base` mention is stale.** The kmp/lifecycle prompts mentioned `LoggingBaseActivity`. On this branch `MainActivity` extends `ComponentActivity` directly; there is no `:base` module. No bug, but worth fixing the prompt template the next time the dispatcher runs.
- **Ktor exception path not end-to-end traced** for BUG-003. The conclusion that `refreshDeals` can throw upstream of the DB collector is supported by reading the call chain; full reproduction would require a manual airplane-mode test of the Store screen's retry path.
- **Low-confidence findings (BUG-005, BUG-006, BUG-007, BUG-009, BUG-010)** all flag latent foot-guns — patterns that are safe in current usage but would activate on plausible near-future changes (real Sentry on iOS, runtime locale switching, partial-field SearchViewModel callers, "Reset filters" UX, etc.). Treat these as advisory, not as bugs to ship a fix for today.
- **No native (iOS Swift), test, or build-script code was scanned** — only production Kotlin sources.
- **Worktrees and build outputs excluded.**
