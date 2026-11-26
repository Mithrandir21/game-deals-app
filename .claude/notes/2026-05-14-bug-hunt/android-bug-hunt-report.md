# Android Bug Hunt — Report

Branch scanned: `dev`. Tests excluded. Production sources in `app/src/main`, `*/src/commonMain`, `*/src/androidMain`, `*/src/iosMain`.

## Summary

- Total findings: **15** (+ 2 informational)
- Critical: 0 · High: 2 · Medium: 3 · Low: 10
- Specialists run: coroutine-and-flow-defects, lifecycle-leak-hunter, compose-correctness, main-thread-violations, resource-leaks, kmp-defects
- Modules scanned: app + 7 feature modules + domain + remote/** + common/** + common/ui + logging

The codebase is unusually clean overall: suspend-first repositories, Flow-first DAOs, every screen uses `collectAsStateWithLifecycle`, one-shot events are routed through `SingleEventEffect`+`repeatOnLifecycle`, and no `runBlocking`/`GlobalScope`/`Thread.sleep`/`allowMainThreadQueries` in production. **The two High-severity findings are both on the iOS side** — Android-only users are largely unaffected.

## Quick-win table

| ID | Sev | Category | Location | Effort | Conf | Title |
|---|---|---|---|---|---|---|
| BUG-001 | High | KMP / platform asymmetry | `common/src/androidMain/.../PlatformDateFormatter.android.kt:10-16` | Small | High | Android date formatter caches locale + timezone at class-load |
| BUG-002 | High | iOS / UIKit | `common/ui/src/iosMain/.../PlatformActions.ios.kt:8-18` | Small | High | iOS share uses deprecated `keyWindow` and crashes on iPad |
| BUG-003 | Medium | Compose stability | `feature/{home,store,search}/.../*Screen.kt` | Small | High | `Set<Int>` favourite-ids parameter is unstable, causes over-recomposition |
| BUG-004 | Medium | UX wiring | `feature/store/.../StoreScreen.kt:140-148`, `feature/favourites/.../FavouritesScreen.kt:161-169` | Small | High | Snackbar "Retry" action calls `onBack()`, no retry function exists |
| BUG-005 | Medium | Room KMP | `domain/src/iosMain/.../DomainIosModule.kt:12-21` (+ Android sibling) | Small | Medium | Room builder missing `setQueryCoroutineContext(Dispatchers.IO)` |
| BUG-006 | Low | SharedPreferences | `common/src/androidMain/.../SharedPreferencesBackend.kt:10,18` | Trivial | High | `readString`/`contains` missing `@WorkerThread` parity |
| BUG-007 | Low | Compose stability | `feature/game/.../GameViewModel.kt:148`, `common/ui/.../DealBottomSheet.kt:230` | Small | High | `Pair<Store, GameDeal>` makes list parameter unstable |
| BUG-008 | Low | Swift interop | `common/src/commonMain/.../Destination.kt:14-36` | N/A | High | Sealed `Destination` loses Swift exhaustiveness (no SKIE) |
| BUG-009 | Low | Coroutine sharing | `feature/giveaways/.../GiveawaysViewModel.kt:65` | Trivial | Medium | `SharingStarted.Eagerly` over `combine` keeps Room observation hot |
| BUG-010 | Low | Race condition | `domain/src/commonMain/.../FavouritesRepository.kt:48` | Small | Medium | `toggleFavourite` performs non-atomic read-modify-write (TOCTOU) |
| BUG-011 | Low | Compose remember key | `feature/search/.../SearchScreen.kt:385,390` | Small | Medium | `rememberSaveable` re-keys on rebuilt range; slider snap on release |
| BUG-012 | Low | Cold-start | `app/src/main/.../GameDealsApplication.kt:47,120-126` | Small | Medium | `Sentry.init` runs on Main during `onCreate` (dormant: DSN is empty) |
| BUG-013 | Low | Swift interop | `remote/src/commonMain/.../HttpClientFactory.kt:14` | Small | Medium | `httpClient` default-arg on `expect` lost to Swift |
| BUG-014 | Low | WebView | `feature/webview/src/androidMain/.../WebView.android.kt:67-88` | Small | Low | `WebView` constructed with Activity `Context` (teardown is correct) |
| BUG-015 | Low | Structured concurrency | `app/src/main/.../GameDealsApplication.kt:104-118` | Trivial | Low | Unmanaged `CoroutineScope` for DB warm-up |

Informational (no-fix-required, kept for context): `Dispatchers.IO`-aliases-`Default`-on-iOS comment, NSLog `%`-escape — both intentional and documented.

---

## Findings (full detail)

### BUG-001: Android `formatLocaleAwareDate` caches locale and timezone at class-load

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | KMP cross-platform actual asymmetry |
| **Location** | `common/src/androidMain/kotlin/pm/bam/gamedeals/common/datetime/formatting/PlatformDateFormatter.android.kt:10-16` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** A `private val androidFormatter` is built once with `Locale.getDefault()` and `ZoneId.systemDefault()` at class load. The iOS actual (`PlatformDateFormatter.ios.kt:13-20`) reads `NSLocale.currentLocale` / `NSTimeZone.systemTimeZone` on every call.

**Impact.** Behaviour drifts between platforms whenever the user changes locale or timezone in Settings. iOS picks the change up immediately; Android keeps the boot-time locale/zone until process death — wrong-language month names and date strings off by hours/days after a timezone change.

**Recommended fix.** Build the `DateTimeFormatter` inside the function (or rebuild on locale/timezone broadcasts).

**Confidence rationale.** Direct code reading; classic `DateTimeFormatter.withLocale(Locale.getDefault())` mistake.

---

### BUG-002: `IosPlatformActions.share` uses deprecated `keyWindow` and crashes on iPad

| Field | Value |
|---|---|
| **Severity** | High |
| **Category** | iOS / UIKit API misuse |
| **Location** | `common/ui/src/iosMain/kotlin/pm/bam/gamedeals/common/ui/platform/PlatformActions.ios.kt:8-18` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** Two compounding issues:
1. `UIApplication.sharedApplication.keyWindow` is deprecated since iOS 13; in multi-scene apps it can return `nil`, in which case the share sheet silently never appears.
2. `UIActivityViewController` presented on iPad must have its `popoverPresentationController` configured (`sourceView`/`sourceRect` or `barButtonItem`). Without it, UIKit raises `NSInvalidArgumentException` and crashes the app.

**Impact.** Share silently fails on modern multi-scene iPhones and **crashes the app on iPad** the first time a user taps share.

**Recommended fix.** Resolve the key window via `UIApplication.connectedScenes` and configure `popoverPresentationController?.sourceView`/`sourceRect` before presenting.

**Confidence rationale.** Both behaviours are documented UIKit constraints since iOS 8 (popover) and iOS 13 (`keyWindow`).

---

### BUG-003: `Set<Int>` favourite-ids parameter is unstable and forces over-recomposition

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Compose stability / over-recomposition |
| **Location** | `feature/home/.../HomeScreen.kt:223`, `feature/store/.../StoreScreen.kt:223`, `feature/search/.../SearchScreen.kt:160`; producing VMs `HomeViewModel.kt:72`, `StoreViewModel.kt:47`, `SearchViewModel.kt:47` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `kotlin.collections.Set` is classified as **unstable** by the Compose compiler. Every Room emission from `observeFavouriteIds()` produces a fresh `Set` instance; the receiving `*Content` composables can never be skipped — Compose recomposes the entire subtree, including the `LazyColumn` of deal rows that closes over `favouriteIds`.

**Impact.** Wasted recomposition on every favourite toggle / Room invalidation, even when nothing visible changed. Worst on Home and Store where the lists are longest. Observable as jank when favouriting items.

**Recommended fix.** Use `kotlinx.collections.immutable.ImmutableSet<Int>` (or `PersistentSet<Int>`) on the public API. Map via `.toImmutableSet()` in the VM. The project already depends on `kotlinx-collections-immutable` and uses `ImmutableList` consistently — `Set` was overlooked.

**Confidence rationale.** Compose's stability rule for `kotlin.collections.Set` is documented behavior.

---

### BUG-004: Snackbar "Retry" action handler calls `onBack()`, no retry function exists

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | UX wiring / broken feature |
| **Location** | `feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreen.kt:140-148`, `feature/favourites/src/commonMain/kotlin/pm/bam/gamedeals/feature/favourites/ui/FavouritesScreen.kt:161-169` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** Error snackbars use action label `errorRetry`, but the action handler invokes `currentOnBack()`. The underlying `StoreViewModel` and `FavouritesViewModel` expose no retry function, so the user is sent back to the previous screen instead of having the failed request re-attempted.

**Impact.** Users who hit a network/database error on Store or Favourites screens see a "Retry" button that does the opposite of retry. Broken error UX for everyone affected by an error condition on these two screens.

**Recommended fix.** Either (a) add a `retry()` method to `StoreViewModel`/`FavouritesViewModel` and wire the action to it, or (b) relabel the action to match the actual `onBack` behaviour.

**Confidence rationale.** Behaviour mismatch is verifiable directly from the file:line locations cited.

---

### BUG-005: Room builder does not call `setQueryCoroutineContext`

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Room KMP wiring / dispatcher assumption |
| **Location** | `domain/src/iosMain/kotlin/pm/bam/gamedeals/domain/di/DomainIosModule.kt:12-21` and `domain/src/androidMain/kotlin/pm/bam/gamedeals/domain/di/DomainAndroidModule.kt` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** The iOS `RoomDatabase.Builder` calls `setDriver(BundledSQLiteDriver())` but omits `setQueryCoroutineContext(Dispatchers.IO)`. Android's builder also lacks it (the `Executors.newSingleThreadExecutor()` there is only the *query callback* executor, not the query executor).

**Impact.** Without an explicit pinned dispatcher, suspending DAO calls run on whatever context the caller is on, including `Dispatchers.Main` on iOS — potential jank or beachballs. Bundled SQLite driver work is synchronous under the hood.

**Recommended fix.** Add `.setQueryCoroutineContext(Dispatchers.IO)` (or a dedicated single-thread context) in both `DomainIosModule.kt` and `DomainAndroidModule.kt`.

**Confidence rationale.** Recommended pattern from Room-KMP guidance; absence isn't a guaranteed bug under all DAO usage, hence Medium.

---

### BUG-006: `SharedPreferencesBackend.readString` / `contains` missing `@WorkerThread`

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | SharedPreferences first-touch / annotation parity |
| **Location** | `common/src/androidMain/kotlin/pm/bam/gamedeals/common/storage/SharedPreferencesBackend.kt:10, 18` |
| **Effort** | Trivial |
| **Confidence** | High |

**Description.** `writeString`/`remove` carry `@WorkerThread`; `readString`/`contains` don't. Class is only used by `StorageImpl`, which wraps every backend call in `withContext(ioDispatcher)`. The asymmetric annotation makes it easier for a future caller to grab the backend directly and hit a Main-thread first-touch (typical 5-50 ms cold).

**Impact.** None today; latent maintenance hazard.

**Recommended fix.** Add `@WorkerThread` to `readString` and `contains` for parity.

**Confidence rationale.** Annotation gap verified directly; impact bounded by the fact no caller bypasses `StorageImpl`.

---

### BUG-007: `Pair<Store, GameDeal>` makes wrapping list parameter unstable

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Compose stability |
| **Location** | `feature/game/.../GameViewModel.kt:148`, consumers `GameScreen.kt:129,159`; same shape in `common/ui/.../DealBottomSheet.kt:230` |
| **Effort** | Small |
| **Confidence** | High |

**Description.** `kotlin.Pair` is not `@Immutable`; the Compose compiler treats `Pair<A, B>` as unstable regardless of `A`/`B`. Wrapping it in `ImmutableList` doesn't help — the element type still makes the parameter unstable for skippability.

**Impact.** `CompactGameDealsDetails` / `WideGameDealsDetails` / `DealContent` can't be skipped when parents recompose with an equal list. Small practical cost; lists are short.

**Recommended fix.**
```kotlin
@Immutable
data class StoreDealPair(val store: Store, val deal: GameDeal)
```
Then use `ImmutableList<StoreDealPair>`.

**Confidence rationale.** Pair instability in Compose is well-documented; fix is mechanical.

---

### BUG-008: Sealed `Destination` loses Swift exhaustiveness (no SKIE)

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Swift-interop hazard |
| **Location** | `common/src/commonMain/kotlin/pm/bam/gamedeals/common/navigation/Destination.kt:14-36` |
| **Effort** | N/A (informational) |
| **Confidence** | High |

**Description.** Seven sealed `Destination` cases; project does not use SKIE. Swift `switch` over `Destination` would not be exhaustive.

**Impact.** Zero today — only the Kotlin-side Compose nav graph touches it. Latent for any future Swift consumer.

**Recommended fix.** Adopt SKIE before exposing `Destination` to Swift directly.

---

### BUG-009: `SharingStarted.Eagerly` keeps Room observation hot for VM lifetime

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Coroutine sharing — hot upstream kept alive longer than needed |
| **Location** | `feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:65` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** `uiState` is built from `combine(giveawaysFlow, refreshOutcomeFlow, loadingFlow)` and shared with `SharingStarted.Eagerly`. The combine causes the upstream `giveawaysRepository.observeGiveaways()` Room flow to collect immediately on ViewModel construction and stay collecting until `onCleared()` regardless of whether the screen is currently subscribed. The other 5 ViewModels use `WhileSubscribed(5_000)`.

**Impact.** User who navigates away from Giveaways but keeps the ViewModel around (back stack) continues to pay for Room change-tracking and the `combine` body. No correctness bug; efficiency only.

**Recommended fix.** Switch to `SharingStarted.WhileSubscribed(5_000)` to match the rest of the codebase.

**Confidence rationale.** Behaviour well-defined; impact small. Flagged because it diverges from the prevailing convention.

---

### BUG-010: `FavouritesRepository.toggleFavourite` performs non-atomic read-modify-write (TOCTOU)

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Race condition — non-atomic read-modify-write on a hot Flow |
| **Location** | `domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/favourites/FavouritesRepository.kt:48` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** `toggleFavourite` resolves the current state by collecting the first emission of `favouritesDao.observeIsFavourite(gameId)` and then performs an `add`/`remove` based on that snapshot. Three call sites (`HomeViewModel.toggleFavouriteFromDeal`, `StoreViewModel.toggleFavouriteFromDeal`, `GameViewModel.toggleFavourite`) each launch in `viewModelScope`. Two near-simultaneous toggles can both observe the same value and both perform the same action.

**Impact.** Small today — row-level operations are idempotent and there is no monotonic count. Genuine TOCTOU; will break subtly if a feature like "favourite count" or "first-added timestamp must increase" is added later.

**Recommended fix.** Push the toggle to a single DAO `@Transaction` method (`SELECT EXISTS` + `INSERT`/`DELETE` atomically), or wrap in `domainDatabase.useWriterConnection { transactor.immediateTransaction { ... } }` as `DealsRepository.refreshDeals` already does.

**Confidence rationale.** TOCTOU is provable from source. Severity Low because rapid double-tap is unusual at UI cadence.

---

### BUG-011: `rememberSaveable` re-keys on rebuilt range, causing post-release slider snap

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Compose remember key churn |
| **Location** | `feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt:385,390` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** In `Filters`, `existingPriceRange = existingLowest..existingHighest` is constructed inline (line 385) and used as the key for `rememberSaveable(existingPriceRange, ...)`. On `onValueChangeFinished`, `onPriceChanged(round(start), round(end))` propagates rounded `Int` values back into `existingLowest`/`existingHighest`. The recomputed range differs from the user-dragged float range; the `rememberSaveable` key changes; `priceSliderValue` resets; the slider thumb visibly snaps after release.

**Impact.** Minor visual glitch — thumb snaps to integer positions when user releases. Not a crash.

**Recommended fix.** Drop the key (initial value already captured by the lambda):
```kotlin
var priceSliderValue by rememberSaveable(stateSaver = floatRangeSaver) {
    mutableStateOf(existingPriceRange)
}
```

**Confidence rationale.** Mechanism is clear; intent is the only unknown — hence Medium.

---

### BUG-012: `Sentry.init` runs on Main during `Application.onCreate` (currently dormant)

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Cold-start / Main-thread (dormant) |
| **Location** | `app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt:47, 120-126` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** `GameDealsApplication.onCreate()` invokes `initSentry()` directly on Main. Today the DSN is the empty-string constant `SENTRY_DSN = ""`, so the call returns immediately at the `if (SENTRY_DSN.isEmpty()) return` guard — the violation is dormant. Risk materialises the moment a real DSN is wired in.

**Impact.** None today; potential Medium-grade TTI cost on cold start once a DSN is configured.

**Recommended fix.** When a DSN is wired up, dispatch `Sentry.init { ... }` onto a background scope (e.g. the existing `SupervisorJob() + Dispatchers.IO` used by `warmDomainDatabase()`), or use `androidx.startup` with a background `Initializer`.

**Confidence rationale.** Dormancy verified by reading the constant.

---

### BUG-013: `httpClient` default-arg on `expect` lost to Swift

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Swift-interop hazard |
| **Location** | `remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/HttpClientFactory.kt:14` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** `expect fun httpClient(block: HttpClientConfig<*>.() -> Unit = {}): HttpClient`. Defaults are not exposed to Swift. Android + iOS actuals correctly omit the default (per Kotlin rules), so this is purely a Swift-API ergonomics issue.

**Impact.** Today: none — only Kotlin call sites. Latent if shared code is ever called directly from Swift without SKIE.

**Recommended fix.** Add a parameterless overload in `commonMain` or accept and document.

---

### BUG-014: `WebView` constructed with Activity `Context`

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Lifecycle / WebView Activity Context retention |
| **Location** | `feature/webview/src/androidMain/kotlin/pm/bam/gamedeals/feature/webview/ui/WebView.android.kt:67-88` |
| **Effort** | Small |
| **Confidence** | Low |

**Description.** `AndroidView.factory` receives the composition `Context`, which inside `MainActivity`'s `setContent` is the Activity. `WebView(context)` retains the Activity through its internal context chain. `webView.destroy()` is called in `onRelease`, which is the correct mitigation, but `android.webkit.WebView` is well known to leak Activity instances in edge cases.

**Impact.** If `onRelease` ever fails to fire, the Activity reference is retained until process death. Given destroy logic is present, realistic exposure is minimal.

**Recommended fix.** None required. For hardening, consider constructing the `WebView` with a `createConfigurationContext` wrapper around `applicationContext`.

**Confidence rationale.** Low — standard teardown is present. Concern is the general WebView class-of-bug, not a defect here.

---

### BUG-015: Unmanaged `CoroutineScope` for DB warm-up

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Structured concurrency |
| **Location** | `app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt:104-118` |
| **Effort** | Trivial |
| **Confidence** | Low |

**Description.** `warmDomainDatabase()` constructs `CoroutineScope(SupervisorJob() + Dispatchers.IO)` locally and launches into it without storing or binding it. The scope reference is dropped at function return.

**Impact.** Not a lifecycle leak — the work is intentionally Application-scoped. However, if the coroutine ever stalls there is no handle to cancel it.

**Recommended fix.** Keep a single `CoroutineScope` as an `Application` field with `SupervisorJob()` and reuse it for any future app-scoped fire-and-forget work.

**Confidence rationale.** Low — stylistic / structured-concurrency note. Scope holds no Activity/View/Fragment reference; does not meet the lifecycle-leak rubric.

---

## Specialists that found nothing

- **android-bug-hunting-resource-leaks** — Zero findings. Ktor `body<T>()` consumes responses; KSP-generated Room DAOs hide raw cursors; no file/stream/media APIs in production; `WebView.onRelease` is textbook. No surface for classic resource-leak bugs.

## Notes and limitations

- **Test sources were not scanned** (`commonTest`, `androidUnitTest`, `androidInstrumentedTest`, `iosTest`). Bugs in test fixtures that affect CI signal are out of scope for this report.
- **iOS scope.** The two High findings (BUG-001, BUG-002) and Medium BUG-005 are iOS-side defects. If iOS is not yet a shipping target, treat as ship-blockers *before* the first iOS release; Android-only users are unaffected by BUG-002 entirely, but BUG-001's Android-side caching is observable on Android today.
- **No native or obfuscated code analyzed.**
- **Low-confidence items (BUG-014, BUG-015)** are documented but human-judgment items — neither has a confirmed runtime reproducer.

## Suggested fix order

1. **BUG-002** (iOS share crash) — ship-blocker for any iOS release; trivial to fix.
2. **BUG-001** (Android date formatter caches locale/timezone) — user-facing on Android today.
3. **BUG-004** (snackbar "Retry" calls `onBack`) — broken error UX.
4. **BUG-003** (`Set<Int>` instability) — observable jank when favouriting.
5. **BUG-005** (Room dispatcher pinning) — preventive; affects both platforms.
6. The rest are Low-severity and can be batched.
