---
**Path scope:** `feature/*/src/commonMain/kotlin/**/ui/**/*.kt`, `common/ui/src/commonMain/kotlin/**/ui/**/*.kt`, `common/src/commonMain/kotlin/**/ui/**/*.kt`, `app/src/main/java/**/ui/**/*.kt`
**Last surveyed:** 34b01013 on 2026-05-18
---

# Compose Correctness

Composables in this codebase are screen-layer or shared UI components, with strict separation between presentation logic (hoisted state) and reusable building blocks. Effect handlers are used consistently, stability is explicitly marked on every UI-tree type, and preview conventions are uniform.

## Patterns

### `LaunchedEffect` with Explicit Keys + `SingleEventEffect`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all 6 screens + theme

**The pattern.**
Side effects run inside `LaunchedEffect` with explicit dependency keys, never at module scope or in composable lambda bodies. Theme setup applies status-bar color via `LaunchedEffect(colorScheme.primary, darkTheme)`. Screen error snackbars are keyed on `snackbarHostState`. One-shot events (navigation, analytics) flow through a project-defined `SingleEventEffect` composable that wraps `LaunchedEffect`, scopes collection via `repeatOnLifecycle(STARTED)`, and captures lambdas safely.

**Why this works for us.**
Explicit keys make recomposition discipline visible — effects fire only when dependencies actually change. Lifecycle-aware event collection prevents leaks on navigation away. Wrapping the collector in `rememberUpdatedState` lets callers (e.g., navigation lambdas) be closed over without stale captures.

**Known trade-offs / when it strains.**
Snackbar effects must be keyed on the `snackbarHostState` object itself; state transitions alone are not a sufficient key, so the parent must hold the state across recompositions. `SingleEventEffect` re-triggers if the same event is emitted twice — correct for one-shot navigation, but upstream must dedupe if needed.

**How to apply it.**
```kotlin
LaunchedEffect(snackbarHostState) {
  val result = snackbarHostState.showSnackbar(
    message = errorMessage,
    actionLabel = retryLabel
  )
  if (result == SnackbarResult.ActionPerformed) currentOnRetry()
}

SingleEventEffect(viewModel.events) { event ->
  when (event) { is NavigateToGame -> goToGame(event.gameId) }
}
```

**Seen in.**
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/theme/Theme.kt
- common/src/commonMain/kotlin/pm/bam/gamedeals/common/CommonFlowExtensions.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeScreen.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameScreen.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysScreen.kt
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreen.kt

**Deep dive (senior).**
`SingleEventEffect` uses `repeatOnLifecycle(STARTED)` to collect events only in the resumed state, preventing re-collection on pause/resume cycles. The event Flow itself is the key, so emitting the same event twice will re-trigger — intentional for one-shot effects.

### `rememberUpdatedState` for Captured Lambdas in Side Effects

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** 5 screens + common

**The pattern.**
When a side-effect closure (e.g., `onRetry` callback used inside `LaunchedEffect`) captures a lambda parameter that may change across recompositions, the lambda is wrapped via `rememberUpdatedState(lambda)` before use inside the effect. Used systematically across HomeScreen, GameScreen, SearchScreen, GiveawaysScreen, StoreScreen, and the shared `SingleEventEffect`.

**Why this works for us.**
Lambdas are inherently unstable (by-reference captures). A naive `LaunchedEffect(onRetry)` would re-fire whenever the lambda identity changes. `rememberUpdatedState` wraps the lambda in a stable holder so the effect key never changes, yet the closure always reads the latest callback at invocation time.

**Known trade-offs / when it strains.**
Adds one CompositionLocal-style read per effect. Not needed if the lambda is already memoized (e.g., via `remember(dependency)`). Unnecessary if the lambda is never used inside an effect.

**How to apply it.**
```kotlin
val currentOnRetry by rememberUpdatedState(onRetry)

LaunchedEffect(snackbarHostState) {
  if (snackbarHostState.showSnackbar(...) == SnackbarResult.ActionPerformed) {
    currentOnRetry()  // always latest, never stale
  }
}
```

**Seen in.**
- common/src/commonMain/kotlin/pm/bam/gamedeals/common/CommonFlowExtensions.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeScreen.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameScreen.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysScreen.kt
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreen.kt

### `rememberSaveable` with Custom Savers for Complex State

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** SearchScreen, GiveawaysScreen

**The pattern.**
UI filter and search state (search parameters, slider ranges, toggles) is persisted across configuration changes via `rememberSaveable` with custom `mapSaver` and `listSaver` lambdas. Search uses `mapSaver` for `SearchParameters` and `listSaver` for `ClosedFloatingPointRange<Float>`. Giveaways uses `mapSaver` for `GiveawaySearchParameters`. Simpler types (booleans, strings) use the default bundle-based saver.

**Why this works for us.**
Configuration changes (rotation, language switch) blow away non-persisted state. `rememberSaveable` restores filter state automatically, so users don't lose their selections. Custom savers serialize complex domain objects without boilerplate.

**Known trade-offs / when it strains.**
Complex savers add serialization overhead. If a filter state is large or contains non-serializable types, persistence becomes infeasible — keep that in the ViewModel instead. Saver lambdas are not type-safe; mistakes in key names or types fail at runtime.

**How to apply it.**
```kotlin
private val parametersSaver = mapSaver(
  save = { it.asMap() },
  restore = { SearchParameters.from(it) }
)

private val floatRangeSaver = listSaver<ClosedFloatingPointRange<Float>, Any>(
  save = { listOf(it.start, it.endInclusive) },
  restore = { (it[0] as Float)..(it[1] as Float) }
)

var params by rememberSaveable(stateSaver = parametersSaver) {
  mutableStateOf(SearchParameters())
}
```

**Seen in.**
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysScreen.kt

### `@Immutable` on All UI-Tree Types

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** domain models, UI state classes, data holders

**The pattern.**
All sealed classes, data classes, and value objects that flow through the Compose tree are explicitly annotated `@Immutable`. This includes domain models (Deal, Store, Release, Giveaway, Game, GameDetails), ViewModel UI state (HomeScreenData, GameScreenData, SearchData, GiveawaysScreenData), and UI containers (DealBottomSheetData, CustomSpaces). With Kotlin 2.3 / Compose Multiplatform 1.11 on K2, the compiler now honors `@Immutable` on a sealed parent for its data-class branches *within a module* — see the "Parent-class `@Immutable` Trust" entry below — so child annotations on those branches have been dropped (PR #174 removed 8 of ~18 nested annotations with no baseline flips). Top-level cross-module types still require explicit `@Immutable`: K2 cross-module stability inference remains unreliable, so anything consumed from another module keeps its annotation (L-2026-05-18-03).

**Why this works for us.**
The `@Immutable` annotation tells the Compose compiler that instances are never mutated, enabling aggressive recomposition skipping when a parameter is unchanged — essential for performance in list items and deeply nested composables. Parent-trust within a module also cuts annotation noise on sealed hierarchies without losing the guarantee.

**Known trade-offs / when it strains.**
Every field in an `@Immutable` class must itself be immutable (or appear immutable via `val`). Mutable collections (`List` vs `MutableList`) require care — use `kotlinx.collections.immutable` types for any collection field (see "Immutable Collections" below); lambda fields must be stable. Removing an annotation on a child only when the parent is `@Immutable` and the consumer lives in the same module — a cross-module call site can flip the verdict.

**How to apply it.**
```kotlin
@Immutable
sealed class DealBottomSheetData(
  open val store: Store,
  open val gameName: String
) {
  // No @Immutable needed here — same module, sealed parent is @Immutable.
  data class DealDetailsData(
    override val store: Store,
    override val gameName: String,
    val priceDenominated: String
  ) : DealBottomSheetData(store, gameName)
}

@Immutable
data class CustomSpaces(
  val small: Dp = 8.dp,
  val medium: Dp = 12.dp
)
```

**Seen in.**
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/DealBottomSheet.kt
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/theme/Spacing.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/models/

**Related lessons.** L-2026-05-02-02, L-2026-05-15-05, L-2026-05-17-01, L-2026-05-17-02, L-2026-05-17-03

### CI Stability Gate: `debugStabilityCheck` against committed `.stability` baselines

**Status:** established
**First documented:** 2026-05-17   **Last verified:** 2026-05-18 @ 34b01013

**The pattern.**
Every Compose-using module ships a committed `<module>/stability/<module>-debug.stability` snapshot listing every `@Composable` with its `skippable` / `restartable` verdict and per-parameter `STABLE | UNSTABLE` reason. These are the regression contract. The `Build` job in `.github/workflows/android.yml` runs `./gradlew debugStabilityCheck` after `build test`, which diffs the live build against the committed baselines and fails the PR on any drift.

**Why this works for us.**
Without this gate, a future `data class` that quietly drops `@Immutable`, or adds a `kotlin.collections.List` field, would silently flip a row composable from `skippable` to non-skippable — a real perf cliff that's invisible at code review. The check turns "did the stability change?" from a manual `Compose Stability Analyzer` plugin scan into an automated PR-blocking signal.

**Known trade-offs / when it strains.**
Intentional API changes (renaming a Composable, adding a parameter, deliberately accepting a less-stable type) require regenerating the baselines and committing the diff alongside the source change. The baseline diff is itself reviewable and load-bearing — the PR description should justify a stability *loss*.

**How to apply it — when `Build` fails on stability check.**

1. Read the CI log. The failure message names the offending composable and parameter, e.g.:
   ```
   Stability check failed!
   ~ pm.bam.gamedeals.feature.home.ui.ReleaseRow: skippable changed from true to false
   ~ pm.bam.gamedeals.feature.home.ui.ReleaseRow(release): stability changed from STABLE to UNSTABLE
   ```
2. **If the regression is accidental** (likely the common case): find what changed in the named class. Typical causes — a missing `@Immutable` on a new `:domain` model, a raw `List<…>` field added where `ImmutableList<…>` was used, a `Pair<…>` introduced without a named `@Immutable` wrapper. Fix the root cause; the check goes green automatically.
3. **If the regression is intentional**: locally regenerate the affected baseline(s) and commit the diff:
   ```bash
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
     ./gradlew debugStabilityDump
   git add '**/stability/*.stability'
   git commit -m "stability(baseline): accept <T>.foo flipping to UNSTABLE — <one-line reason>"
   ```
   The reason line shows up in PR review and in `git blame` later. Don't regenerate baselines silently.

**Seen in.**
- `.github/workflows/android.yml` — the `Compose Stability Check` step in the `build` job
- `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidApplicationConventionPlugin.kt` and `KotlinMultiplatformLibraryComposeConventionPlugin.kt` — where the `com.github.skydoves.compose.stability.analyzer` plugin is applied
- `<module>/stability/<module>-debug.stability` — the 9 baseline files (one per Compose-using module)

**Related lessons.** L-2026-05-17-03, L-2026-05-17-04

### State Hoisting: Private `Screen` + Public Entry-Point Composable

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all feature screens

**The pattern.**
Each feature screen splits into a thin public entry point (e.g., `HomeScreen`) that pulls data from the ViewModel via `collectAsStateWithLifecycle()` and dispatches callbacks back, plus a private reusable `Screen` composable that takes all state and callbacks as parameters. The private screen is pure — no ViewModel or Flow dependency. Callbacks are simple `() -> Unit` or `(id: Int) -> Unit`. State is always immutable data, annotated `@Immutable`.

**Why this works for us.**
Hoisting state to the public composable makes the pure screen testable and previewable in isolation without mocking a ViewModel. The ViewModel stays thin and focused. Callback parameters are self-documenting — callers see exactly what events the screen can emit.

**Known trade-offs / when it strains.**
Large screens with many parameters become hard to read. Callback chains become tedious if events must hop through intermediate composables. When parameter counts climb, extract sub-composables.

**How to apply it.**
```kotlin
@Composable
internal fun HomeScreen(
  onSearch: () -> Unit,
  goToGame: (Int) -> Unit,
  viewModel: HomeViewModel = koinViewModel()
) {
  val data by viewModel.uiState.collectAsStateWithLifecycle()
  Screen(
    data = data,
    onSearch = onSearch,
    goToGame = goToGame,
    onRetry = { viewModel.loadTopStoresDeals() }
  )
}

@Composable
private fun Screen(
  data: HomeScreenData,
  onSearch: () -> Unit,
  goToGame: (Int) -> Unit,
  onRetry: () -> Unit
) {
  Scaffold(...) { /* render data, invoke callbacks */ }
}
```

**Seen in.**
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeScreen.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameScreen.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysScreen.kt
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreen.kt

### Theme Composable with `CompositionLocalProvider` Spacing

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** `GameDealsTheme` wrapper, used by every screen

**The pattern.**
`GameDealsTheme(darkTheme, dynamicColor, content)` is declared as `expect fun` in `commonMain` and has platform `actual` implementations. The Android `actual` applies `dynamicDarkColorScheme(LocalContext.current)` / `dynamicLightColorScheme(LocalContext.current)` on API 31+, manages status-bar color inside a `LaunchedEffect`, and guards `LocalView.current.isInEditMode` so previews don't crash on `Window` access. The iOS `actual` is a thin wrapper around `MaterialTheme` and ignores the `dynamicColor` flag entirely. The shared `CompositionLocalProvider(LocalExtendedSpacing provides CustomSpaces())` wrapper is unchanged and lives in `commonMain` so both platforms expose the same spacing API. All screens wrap their root in `GameDealsTheme`.

**Why this works for us.**
The `expect`/`actual` split keeps the platform-specific bits (Android dynamic color, status bar, preview guards) out of `commonMain` while still giving every screen a single import. Centralizes theme setup so no screen has to think about color, typography, or spacing sources. CompositionLocal keeps custom spacing implicit — not all screens need it, and it's not threaded as a parameter.

**Known trade-offs / when it strains.**
CompositionLocal is implicit, which makes data flow harder to trace. Adding a new theme parameter requires touching `expect` plus each `actual`. iOS deliberately drops dynamic color — visual parity must be checked per-screen.

**How to apply it.**
```kotlin
// commonMain
val LocalExtendedSpacing = staticCompositionLocalOf { CustomSpaces() }

@Composable
expect fun GameDealsTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit
)

// androidMain — actual
@Composable
actual fun GameDealsTheme(darkTheme: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
  val context = LocalContext.current
  val colorScheme = when {
    dynamicColor && SDK_INT >= S -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    darkTheme -> darkScheme
    else -> lightScheme
  }
  val view = LocalView.current
  if (!view.isInEditMode) {
    LaunchedEffect(colorScheme.primary, darkTheme) { /* set status-bar color */ }
  }
  CompositionLocalProvider(LocalExtendedSpacing provides CustomSpaces()) {
    MaterialTheme(colorScheme = colorScheme, content = content)
  }
}

// iosMain — actual
@Composable
actual fun GameDealsTheme(darkTheme: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalExtendedSpacing provides CustomSpaces()) {
    MaterialTheme(colorScheme = if (darkTheme) darkScheme else lightScheme, content = content)
  }
}
```

**Seen in.**
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/theme/Theme.kt
- common/ui/src/androidMain/kotlin/pm/bam/gamedeals/common/ui/theme/Theme.kt
- common/ui/src/iosMain/kotlin/pm/bam/gamedeals/common/ui/theme/Theme.kt

### Preview Conventions with Custom Multi-Preview Annotations

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** 16+ preview composables across feature screens

**The pattern.**
Every screen defines private `@Preview` composables, often composed via custom annotations (`@PhonePortrait`, `@PhoneLandscape`, `@TabletPortrait`, `@FoldablePortrait`, `@DarkMode`, `@LightMode`). These annotations are defined in `common/ui/.../Annotations.kt` and combine the base `@Preview` with device and theme configurations. Previews target the private `Screen` composable using `PreviewData` factories (`PreviewDeal`, `PreviewStore`, `PreviewGameDetails`).

**Why this works for us.**
Meta-annotations apply multiple configurations declaratively. Previewing the pure composable (not the entry point) avoids ViewModel mocking. Preview data is factored so changes propagate automatically.

**Known trade-offs / when it strains.**
Many preview composables clutter the file. Preview data can diverge from real data if not maintained alongside model changes.

**How to apply it.**
```kotlin
// Annotations.kt
@Preview(name = "Phone Portrait", device = Devices.PHONE, showBackground = true)
annotation class PhonePortrait

// Screen.kt
@PhonePortrait
@PhoneLandscape
@Composable
private fun ScreenPreview() {
  Screen(
    data = HomeScreenData(state = SUCCESS, releases = persistentListOf(...)),
    onSearch = {}, onRetry = {}, goToGame = {}
  )
}
```

**Seen in.**
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeScreen.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameScreen.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysScreen.kt
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreen.kt
- feature/webview/src/commonMain/kotlin/pm/bam/gamedeals/feature/webview/ui/WebView.kt
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/DealBottomSheet.kt

### Parent-class `@Immutable` Trust (K2 cross-module inference)

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** sealed-class domain hierarchies with data-class branches

**The pattern.**
With Kotlin 2.3 / Compose Multiplatform 1.11 on the K2 frontend, the Compose compiler honors `@Immutable` on a sealed parent class (or sealed interface) for its data-class branches *as long as both live in the same module*. The branches do not need their own `@Immutable` annotation — the stability baseline records them as STABLE either way. PR #174 dropped 8 of ~18 nested-class annotations on `DealBottomSheetData`, `HomeScreenData` and friends without flipping a single row in any `.stability` baseline. The rule has one important hole: K2 cross-module stability inference is still unreliable, so a top-level type consumed from another module (e.g., a `:domain` model rendered by a `:feature` screen) keeps its explicit `@Immutable`. If the parent is *not* `@Immutable`, the child still needs its own annotation; parent-trust does not cascade through unmarked roots.

**Why this works for us.**
Less annotation noise on sealed hierarchies, and the regression contract (the `.stability` baselines) makes it safe to remove the redundant markers — if we ever break a parent's stability, the gate fails. Keeping the rule scoped to *within a module* matches what the compiler actually proves; we don't over-trust K2.

**Known trade-offs / when it strains.**
A future cross-module move of one branch silently loses its parent's annotation context — `debugStabilityCheck` will catch the flip, but the fix is to re-add `@Immutable` on the branch, not to chase the parent. Be deliberate when moving sealed branches between modules. Also: a parent declared `@Stable` (not `@Immutable`) does *not* grant the same trust; only `@Immutable` cascades.

**How to apply it.**
```kotlin
@Immutable
sealed class DealBottomSheetData(
  open val store: Store,
  open val gameName: String
) {
  // Same module, parent is @Immutable — no annotation needed.
  data class DealDetailsData(
    override val store: Store,
    override val gameName: String,
    val priceDenominated: String
  ) : DealBottomSheetData(store, gameName)

  data class GameInfoData(
    override val store: Store,
    override val gameName: String,
    val gameId: Int
  ) : DealBottomSheetData(store, gameName)
}

// Cross-module: still annotate explicitly.
@Immutable
data class GameDetails( /* consumed from :domain by :feature/game */ )
```

**Seen in.**
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/DealBottomSheet.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt

**Related lessons.** L-2026-05-18-03

### Immutable Collections (`kotlinx.collections.immutable`) in StateFlow payloads

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** domain models (Deal, Game, GameDetails) + every ViewModel `.Data` class

**The pattern.**
Every collection field that flows through the Compose tree — domain models (`Deal`, `Game`, `GameDetails`), ViewModel UI-state holders (`HomeScreenData`, `GameScreenData`, `SearchData`, `GiveawaysScreenData`), and intermediate UI containers — is declared as `ImmutableList<T>` / `ImmutableSet<T>` from `kotlinx.collections.immutable`, not `kotlin.collections.List<T>`. Construction sites use `persistentListOf(...)` / `.toImmutableList()`; previews and tests use `persistentListOf(...)`.

**Why this works for us.**
The Compose compiler cannot prove that a `kotlin.collections.List` parameter is non-mutating — it's an interface that allows `MutableList` underneath — so any composable taking `List<T>` is flagged `UNSTABLE` and forced to recompose unconditionally. `ImmutableList<T>` is a distinct interface that's marked stable, so list rows (`ReleaseRow`, `DealRow`, `GiveawayRow`) stay `skippable`. The `debugStabilityCheck` gate enforces this — accidentally typing a parameter as `List<...>` fails the build with a precise diff.

**Known trade-offs / when it strains.**
Mapping layers must convert (`.toImmutableList()`) at the boundary — typically inside a repository or mapper, not the ViewModel. Java/Kotlin-stdlib APIs that produce `List<T>` (Retrofit responses, Room queries, `map { }`) need explicit conversion. The extra allocation at the boundary is cheap; the savings on recomposition are large.

**How to apply it.**
```kotlin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class HomeScreenData(
  val state: ScreenState,
  val releases: ImmutableList<Release> = persistentListOf(),
  val topDeals: ImmutableList<Deal> = persistentListOf()
)

// In the repository / mapper:
val deals: ImmutableList<Deal> = api.fetchDeals().map { it.toDomain() }.toImmutableList()

// In the composable:
@Composable
private fun DealList(deals: ImmutableList<Deal>) { /* stable parameter, skippable */ }
```

**Seen in.**
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/models/Deal.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/models/Game.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt

**Related lessons.** L-2026-05-02-02, L-2026-05-15-05

### Coil 3 Null-Model Guard for `AsyncImage`

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every `AsyncImage` / `SubcomposeAsyncImage` call site

**The pattern.**
Wherever a Coil 3 `AsyncImage` (or `SubcomposeAsyncImage`) is rendered, the `model` argument is gated on null at the call site: if the URL / `path` is null, the code falls back to a static `Image(painter = placeholder, ...)` instead of passing `null` to Coil. Painter-level `error`/`fallback` parameters are still set for failed loads, but they are not the primary null-guard.

**Why this works for us.**
Coil 3's request builder raises `coil3.request.NullRequestDataException` when `model` is null, *even when `error` / `fallback` painters are supplied* — those painters are consulted for failed loads (HTTP error, decode failure), not for request-build-time rejection of a null model. Gating at the call site avoids the crash without trusting the request builder to short-circuit. Optional cover art (`Game.thumb`, `Store.banner`) is naturally nullable in our domain, so this guard is required, not theoretical.

**Known trade-offs / when it strains.**
The boilerplate is repetitive; every call site repeats the same `if (url != null)` shape. A small `OptionalAsyncImage(url, placeholder)` wrapper composable absorbs it for high-traffic places. The placeholder painter must still be sized correctly — Coil will not lay out the missing image for you.

**How to apply it.**
```kotlin
@Composable
fun GameThumbnail(thumbUrl: String?, modifier: Modifier = Modifier) {
  if (thumbUrl != null) {
    AsyncImage(
      model = thumbUrl,
      contentDescription = null,
      placeholder = painterResource(R.drawable.placeholder),
      error = painterResource(R.drawable.broken_image),
      modifier = modifier
    )
  } else {
    Image(
      painter = painterResource(R.drawable.placeholder),
      contentDescription = null,
      modifier = modifier
    )
  }
}
```

**Seen in.**
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/image/
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeScreen.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameScreen.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt

**Related lessons.** L-2026-05-18-02

## What we don't do

- **No side effects in composable bodies.** No mutations, network calls, or logging directly in composable bodies — all such work runs inside `LaunchedEffect`, `DisposableEffect`, or `SingleEventEffect`. **Why we avoid it:** side effects in the composition phase break recomposition correctness; effects that fire on every recomposition are a recipe for crashes or runaway work.
- **No `remember` without a purpose.** Stateless helper composables (text rows, cards, image chips) don't hoist state unnecessarily — state lives in the screen-layer composable where callbacks can be wired. **Why we avoid it:** unnecessary `remember` hides where state actually lives and breaks reusability.
- **No `@Composable` lambda parameters for events.** Event callbacks are simple non-composable function types. **Why we avoid it:** `@Composable` lambdas nest the composition tree in surprising ways and create stability hazards.
- **No global mutable state.** ViewModels are scoped per screen via Koin's `viewModel { }` (resolved with `koinViewModel()`); no top-level objects or singletons are read from composables.
- **No types in the Compose tree without a stability annotation.** All UI state is `@Immutable` or backed by persistent collections. **Why we avoid it:** unstable parameters force unconditional recomposition and silently waste CPU.
- **No raw `kotlin.collections.List` parameters on composables.** Use `ImmutableList<T>` from `kotlinx.collections.immutable`. **Why we avoid it:** `List<T>` is provably unstable to the Compose compiler — it can't rule out a `MutableList` underneath — so the composable becomes non-skippable.
- **No `AsyncImage(model = nullable)` without a call-site null guard.** **Why we avoid it:** Coil 3 throws `NullRequestDataException` on a null model regardless of `error`/`fallback` painters.
- **No `AndroidView` without lifecycle cleanup.** The webview feature releases the WebView on `onRelease` — stops loading, clears the client, destroys.
