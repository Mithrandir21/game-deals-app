# Findings — Compose Correctness

Hunter: `android-bug-hunting-compose-correctness`
Date: 2026-05-02
Branch HEAD: `wave/2026-05-02-bug-hunt/issue-75-77-giveaways-refresh-outcome`

Found 1 findings (0 Critical, 0 High, 1 Medium, 0 Low).

---

### BUG-001: `SearchParameters.equals` always returns `false`, defeating Compose skipping for any composable taking it

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Compose stability / unstable parameter |
| **Location** | `domain/src/main/java/pm/bam/gamedeals/domain/models/Search.kt:52` |
| **Effort** | Trivial |
| **Confidence** | High |

**Description.** `SearchParameters` is a `data class` whose `equals` is overridden to always return `false`. The override exists to defeat `MutableStateFlow`'s structural-equality conflation (so re-emitting the same parameters re-fires downstream collectors — the comment on lines 49–51 calls this out). The side effect: every `@Composable` taking a `SearchParameters` parameter is forced to recompose on every parent recomposition, because Compose's skipping rule compares parameters with `equals` and an always-`false` `equals` makes the value behave as if it changed every time.

**Impact.**
- `feature/search/.../SearchScreen.kt` passes `existingSearchParameters: SearchParameters` through `Screen` → `SearchFilters` → `Filters`. None of these composables can skip recomposition, so on every keystroke into the search field (`existingParameters.copy(title = it)` on line 117) the entire filters subtree re-evaluates even when its inputs are bit-identical.
- Holding the value in `mutableStateOf(SearchParameters())` (line 94) means each `setValue` triggers a state change even when the new content equals the old.
- The override would also break any future `LaunchedEffect(params)` keyed on `SearchParameters` (re-launch every recomposition) and any `derivedStateOf` that closes over it.

**Evidence.**
```kotlin
// domain/src/main/java/pm/bam/gamedeals/domain/models/Search.kt:48-53
/**
 * Returning `false` to avoid the default implementation of `equals` when attempting to emit a new value in a `StateFlow`.
 * See "Strong equality-based conflation" in the StateFlow documentation.
 */
override fun equals(other: Any?): Boolean = false
```

Composable call sites that take this type as a parameter (none can skip):
- `feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt:94` — held in `mutableStateOf`
- `feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt:114` — passed to `Screen`
- `feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt:134, 197, 309` — `Screen`/`SearchFilters`/`Filters` parameters
- `feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt:339-341` — slider initial-value derivations

**Recommended fix.** Revert this `equals` override and move the non-conflating semantics to the producer side: replace `MutableStateFlow<SearchParameters>` with `MutableSharedFlow<SearchParameters>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)` in `SearchViewModel`, or wrap emissions in an `Indexed<T>` envelope. Removing the override restores data-class `equals`, re-enables Compose skipping, and unblocks `LaunchedEffect`/`derivedStateOf` keying on the type.

**Confidence rationale.** High — the override is verbatim the antipattern described in the SKILL.md D9 detector. The agent reported a parallel wave PR (#88 on `wave/2026-05-02-bug-hunt/issue-76-search-parameters-equals`) may already be fixing this exact instance — github-sync should dedupe. On HEAD of the audited branch, the override is still present at `Search.kt:52`.

---

## Detector clearance notes (no findings)

- **D1 (side effect outside effect handler).** All side-effecting calls (`viewModel.loadX`, `navController.navigate`, snackbar `showSnackbar`, `sideEffectFlow.collect`) are inside `LaunchedEffect`, `SingleEventEffect`, or user-event callbacks.
- **D2 (`LaunchedEffect(Unit/true)`).** Zero instances on HEAD.
- **D3 (`rememberSaveable` non-Saveable types).** All custom-saver call sites (`SearchScreen.kt:94, 346`, `GiveawaysScreen.kt:90`) provide explicit `mapSaver`/`listSaver`s. Other `rememberSaveable` usages hold primitives only.
- **D4 (`remember` without keys).** Every `remember { … }` constructs a composition-local stable value (`SnackbarHostState`, `WebViewClient`, `MutableState<…>`); none memoizes a value derived from a parameter that can change.
- **D5 (`collectAsState()` vs `collectAsStateWithLifecycle()`).** Every screen uses `collectAsStateWithLifecycle()`. No bare `collectAsState()` on HEAD.
- **D6 (mutating state during composition).** No naked `state.value = ...` in composable body. The `lastLoadedUrl = url` writes inside `WebView.kt:111` / `:117` are the documented `AndroidView` lifecycle pattern; the `if (lastLoadedUrl != url)` guard prevents re-invocation loops.
- **D7 (stale lambda capture).** Every error-snackbar `LaunchedEffect` wraps the retry callback with `rememberUpdatedState` (`HomeScreen.kt:184`, `SearchScreen.kt:145`, `GameScreen.kt:302`, `GiveawaysScreen.kt:138`).
- **D8 (`rememberCoroutineScope` misuse).** Zero usages on HEAD.
- **D9 (unstable parameters).** Aside from BUG-001, every domain model used as a composable parameter is `@Immutable` with `ImmutableList` fields. `DealBottomSheetData.DealDetailsData.cheaperStores` remains `List<Pair<…>>`, but the enclosing class is `@Immutable` (DealBottomSheet.kt:311) so the Compose compiler treats it as stable by promise.
- **D10–D15.** No `derivedStateOf`, `produceState`, or `mutableStateOf` exposed from any ViewModel. No `DisposableEffect` in `src/main` (WebView teardown is via `AndroidView.onRelease`). All composition-local reads at top of host composable.
