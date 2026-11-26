# Compose Correctness — Bug Hunt Findings

Scope: `@Composable` functions in production source across all feature and common-ui modules. Detectors D1–D15 run.

**Summary:** Strong Compose hygiene throughout. Two low-severity items; no Critical/High/Medium correctness defects. (0 Critical, 0 High, 0 Medium, 2 Low.)

### BUG-001: App-root search query collected with `collectAsState()` instead of `collectAsStateWithLifecycle()`

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Compose correctness — lifecycle-unaware Flow collection (D5) |
| **Location** | `app/src/main/java/pm/bam/gamedeals/navigation/NavGraph.kt:73`; `iosApp/src/iosMain/kotlin/pm/bam/gamedeals/iosApp/MainViewController.kt:169` |
| **Effort** | Trivial |
| **Confidence** | High (deviates from convention); Low (runtime impact) |

**Description.** Both app roots read `SearchController.activeQuery` with plain `collectAsState()`. Every other screen (20+ sites) uses `collectAsStateWithLifecycle()`. These two are the only plain `collectAsState()` usages in production.

**Impact.** Negligible. The root is composed for the app's entire foreground lifetime, and the upstream is a trivial in-memory `MutableStateFlow` (no Room invalidation/socket/network), so the off-screen-hot-flow cost D5 warns about doesn't materialize. Downside is convention inconsistency only.

**Evidence.**
```kotlin
// NavGraph.kt:73 (and identically MainViewController.kt:169)
val activeSearchQuery by SearchController.activeQuery.collectAsState()
// vs convention, DealsScreen.kt:190:
val activeSearch by SearchController.activeQuery.collectAsStateWithLifecycle()
```

**Recommended fix.** Use `collectAsStateWithLifecycle()` (artifact already on classpath).

**Confidence rationale.** Deviation grep-confirmed; runtime impact genuinely Low because host is always composed and upstream is cheap. Hardening, not a bug fix.

### BUG-002: In-progress (unsubmitted) search text is overwritten on state restoration

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Compose correctness — `rememberSaveable` value clobbered by an effect |
| **Location** | `common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/shell/AppShellScaffold.kt:88,93-95` |
| **Effort** | Small |
| **Confidence** | Medium |

**Description.** `searchText` is hoisted into `rememberSaveable` (survives process death), but `LaunchedEffect(showSearchField, activeSearchQuery)` unconditionally rewrites it to `activeSearchQuery.orEmpty()` whenever the field is shown, so the saved value is never honored.

**Impact.** Minor UX edge case. Typed-but-unsubmitted text is replaced by the last submitted query on restore / effect re-entry; the `rememberSaveable` does no useful work.

**Evidence.**
```kotlin
var searchText by rememberSaveable { mutableStateOf("") }              // :88 — saved...
val showSearchField = manualSearch || (isDealsTab && activeSearchQuery != null)
LaunchedEffect(showSearchField, activeSearchQuery) {                   // :93
    if (showSearchField) searchText = activeSearchQuery.orEmpty()      // :94 — ...but always overwritten
}
```

**Recommended fix.** Either drop to plain `remember` (value never honored anyway), or seed only when `activeSearchQuery` actually changes:
```kotlin
LaunchedEffect(activeSearchQuery) { if (activeSearchQuery != null) searchText = activeSearchQuery }
```

**Confidence rationale.** Medium — overwrite is clear from code, but seeding-on-open may be intentional; the defect is that `rememberSaveable` implies an intent the effect defeats. No stability consequence.

## Detectors clean (notable confirmations)
- **D1:** No naked side effects in composable bodies.
- **D2:** All three `LaunchedEffect(Unit)` sites (`NavGraph.kt:54`, `AppShellScaffold.kt:243`, `SignInPromptHost.kt:50`) collect process-lifetime singleton flows or request focus once — Unit correct.
- **D3:** `GiveawaysScreen.kt:113` uses a proper `mapSaver`; all others hold primitives/enums/Strings.
- **D4:** Parameter-dependent `remember` blocks are keyed (`DealsScreen.kt:290,294,298`, `PriceHistoryChart.kt:91,145-152`).
- **D6:** No state mutation during composition — every `.value =` is in a VM/repo/controller/callback.
- **D7:** `rememberUpdatedState` used everywhere needed (`SingleEventEffect.kt:36`, `HomeScreen.kt:202`, `GamePageScreen.kt:328`, `GiveawaysScreen.kt:164`, both WebView actuals).
- **D8:** Only `rememberCoroutineScope` (`OnboardingScreen.kt:168`) is correctly scoped to onClick pager animations.
- **D11:** No `MutableState` exposed from ViewModels.
- **D12:** Both `DisposableEffect`s (`NotificationPermission.android.kt:51`, `.ios.kt:41`) remove their lifecycle observer.
- **D13:** Both countdowns and `MainActivity.kt:33` set `value` eagerly before suspending.
- **List keys:** Every `items(...)` supplies a stable key.
