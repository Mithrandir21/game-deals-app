---
**Path scope:** `feature/*/src/commonMain/kotlin/**/ui/*ViewModel.kt`, `feature/*/src/commonMain/kotlin/**/ui/*State.kt`, `feature/*/src/commonMain/kotlin/**/ui/*Screen.kt`, `common/ui/src/commonMain/kotlin/**/deal/**`
**Last surveyed:** 34b01013 on 2026-05-18
---

# UI State

UI state in this codebase follows a consistent MVVM shape: ViewModels expose immutable `StateFlow` sources of truth built via `stateIn()` or `asStateFlow()`, and screens consume state through `collectAsStateWithLifecycle()`, dispatching user intent back to the ViewModel through method calls.

## Patterns

### Sealed Screen State with Loading / Error / Data Variants

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all 6 feature ViewModels (Store, Game, Deal, Home, Giveaways, Search)

**The pattern.**
Most feature screens expose a sealed type for their UI state with three variants: `Loading` (initial / retry), `Error` (failure scenarios), and `Data` (the happy path with domain content). The variants give exhaustive `when` coverage in Composables and make invalid states unrepresentable — a screen cannot be both loading and displaying data. Store and Game use this directly; Home and Giveaways use a coarser enum `Status` field inside a single data class.

**Why this works for us.**
Sealed types enforce type safety at compile time; the Compose UI naturally exhausts all branches. Combined with `@Immutable` annotations, this eliminates a large class of concurrency bugs.

**Known trade-offs / when it strains.**
Sealed classes with no constructor args (`data object Loading`) create many small types. When a screen has dozens of independent sub-states (multi-field forms, complex bottom sheets), the pattern can explode into nested sealed types; Home sidesteps this by nesting a `HomeScreenListData` sealed class for list item variants.

**How to apply it.**
```kotlin
sealed class ScreenState {
  data object Loading : ScreenState()
  data object Error : ScreenState()
  data class Data(val content: DomainModel) : ScreenState()
}

// In screen:
val state by viewModel.uiState.collectAsStateWithLifecycle()
when (val s = state) {
  is ScreenState.Loading -> CircularProgressIndicator()
  is ScreenState.Error   -> ErrorUI()
  is ScreenState.Data    -> ContentUI(s.content)
}
```

**Seen in.**
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt

### Private MutableStateFlow + Public StateFlow Source-of-Truth

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all 6 feature ViewModels

**The pattern.**
Every ViewModel maintains a private `MutableStateFlow` (`_uiState`) and exposes a public read-only `StateFlow` via `asStateFlow()`. The ViewModel is the sole writer; consumers receive the immutable view. This encapsulation enforces unidirectional data flow.

**Why this works for us.**
The boundary between private write-access and public read-access is enforced by Kotlin's type system, not convention. Accidental mutation attempts fail at compile time. Koin's `viewModel { }` scopes the instance to the current `NavBackStackEntry`, so the source-of-truth object survives recomposition and config change without being torn down.

**Known trade-offs / when it strains.**
Two properties for one logical stream is verbose. ViewModels with many independent state fields end up with many `_field`/`field` pairs. Stateless presentational composables don't need this; they should accept state as parameters.

**How to apply it.**
```kotlin
class MyViewModel(
  private val repository: MyRepository
) : ViewModel() {
  private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
  val uiState: StateFlow<UiState> = _uiState.asStateFlow()

  fun loadData() {
    viewModelScope.launch {
      _uiState.value = UiState.Loading
      runCatching { repository.fetch() }
        .onSuccess { _uiState.value = UiState.Data(it) }
        .onFailure { _uiState.value = UiState.Error }
    }
  }
}

// Koin binding in the feature's *Module.kt
val myFeatureModule = module {
  viewModel { MyViewModel(get()) }
}

**Seen in.**
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt

### `combine()` Merging Upstream Flows into a Single Screen State

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** Game ViewModel (id + reload trigger), Giveaways ViewModel (giveaways list + refresh outcome)

**The pattern.**
When a screen's state depends on multiple independent flows — e.g., a SavedStateHandle parameter and a manual reload trigger, or a data fetch and a refresh status flag — `combine()` synchronously merges them into a single emitted tuple. The ViewModel then projects that tuple into a unified screen state. The screen never sees intermediate mismatched states.

**Why this works for us.**
`combine()` waits for at least one value from each source before emitting. Game's reload mechanism (combine `gameId` flow with a reload trigger SharedFlow) ensures reloads only kick in once the data is stale. Giveaways combines the giveaway list with a refresh outcome flag without re-fetching.

**Known trade-offs / when it strains.**
`combine()` becomes hard to reason about with 3+ flows; tuple destructuring gets verbose. With 5+ inputs, a custom StateFlow builder is clearer.

**How to apply it.**
```kotlin
init {
  viewModelScope.launch {
    combine(
      idFlow,
      reloadTrigger.onStart { emit(Unit) }
    ) { id, _ -> id }
      .flatMapLatest { id -> loadData(id) }
      .collect { _uiState.emit(it) }
  }
}
```

**Seen in.**
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt

### `WhileSubscribed(5000)` Sharing Strategy for `stateIn()`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** Store ViewModel (screen state + paged deals)

**The pattern.**
When converting a hot Flow into a `StateFlow` via `stateIn()`, use `SharingStarted.WhileSubscribed(5000)`. This keeps the upstream flow active for 5 seconds after the last subscriber leaves, then cancels it. During recomposition or config change, if resubscription happens within that window, the flow stays warm and no redundant re-fetch occurs.

**Why this works for us.**
Configuration changes cause Composables to recompose but the ViewModel survives; within 5 seconds the subscriber re-attaches and the cached state value is immediately available. Without the delay, every rotation would trigger a new collect and a network call.

**Known trade-offs / when it strains.**
If the user navigates away for longer than 5 seconds and returns, the data is stale and must be re-fetched. For app-critical state that must never cancel (auth tokens, global config), prefer `Eagerly` or `Lazily`.

**How to apply it.**
```kotlin
val uiState: StateFlow<Data> = upstreamFlow
  .stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = Data.Loading
  )
```

**Seen in.**
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt

### Shared State Controller for Modal / Bottom-Sheet State

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** Home, Store ViewModels via `DealDetailsController`

**The pattern.**
Modal or bottom-sheet state (load progress, success, error) is delegated to a non-ViewModel controller class that owns its own `MutableStateFlow`. The ViewModel injects the controller and exposes its `StateFlow` alongside the screen's primary state. The controller's methods (`load()`, `dismiss()`) encapsulate the async flow with min-duration delays, error handling, and cancellation. The screen Composable consumes both flows independently.

**Why this works for us.**
Modals are orthogonal to the main screen state; they can be shown/hidden without affecting screen data. Extracting the controller lets multiple screens reuse the same modal logic — Deal details bottom sheet appears in Home, Store, and Game screens. The controller owns its own async context and cancellation.

**Known trade-offs / when it strains.**
Controllers add a layer of indirection and hide state outside the ViewModel. If modal state needs to interact with primary screen state (e.g., dismiss the modal when main data refreshes), the boundaries blur. Testing requires mocking controller dependencies.

**How to apply it.**
```kotlin
private val dealDetailsController = DealDetailsController(
  dealsRepository, storesRepository, logger
)
val dealDetails: StateFlow<DealBottomSheetData?> = dealDetailsController.dealDetails

fun loadDealDetails(dealId: String, ...) {
  dealDetailsController.load(viewModelScope, dealId, ...)
}
```

**Seen in.**
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt

### `SharedFlow` for One-Shot UI Events (Navigation, Snackbar)

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** Home ViewModel

**The pattern.**
One-time events that should not replay (navigation commands, snackbar messages) flow through a `SharedFlow` with `replay = 0`. The ViewModel exposes the public `SharedFlow` and the Composable collects it via `LaunchedEffect` or the project's `SingleEventEffect` helper. Each emission is processed exactly once.

**Why this works for us.**
Distinguishing "state" (stable across recompositions) from "events" (one-shot commands) prevents accidental double-navigation and snackbar spam. Home uses it to emit `NavigateToGame` exactly once when a release is clicked.

**Known trade-offs / when it strains.**
With `replay=0` + `DROP_OLDEST`, if the ViewModel emits before the Composable subscribes (e.g., during rapid screen transitions), the event is lost. For events that absolutely must reach the user (critical errors), a persistent state field is safer.

**How to apply it.**
```kotlin
private val _events = MutableSharedFlow<UiEvent>(
  replay = 0, extraBufferCapacity = 1,
  onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val events: SharedFlow<UiEvent> = _events.asSharedFlow()

// In screen:
SingleEventEffect(viewModel.events) { event ->
  when (event) { is UiEvent.Navigate -> navController.navigate(...) }
}
```

**Seen in.**
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt

### Composable State Collection via `collectAsStateWithLifecycle()`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all 6 feature screens

**The pattern.**
Every feature screen Composable uses `collectAsStateWithLifecycle()` to collect the ViewModel's `StateFlow` into a Compose `State`. Paging flows use the specialized `collectAsLazyPagingItems()` variant. The lifecycle-aware variant automatically pauses collection when the Composable's lifecycle is below STARTED, minimizing battery drain and unnecessary upstream work. The ViewModel itself is obtained from Koin via `koinViewModel()` — the DI seam moved off Hilt with the KMP migration, but the lifecycle-aware collection contract on the screen side is unchanged.

**Why this works for us.**
Lifecycle-aware collection respects both the Compose lifecycle and the underlying Android Lifecycle without manual subscription management. No leaks; no redundant re-fetches on every recomposition. Resolving the ViewModel through `koinViewModel()` keeps the screen `@Composable` callable from `commonMain` source sets, since Koin's Compose integration is multiplatform.

**Known trade-offs / when it strains.**
Lifecycle-aware collection introduces a small latency when the Lifecycle regains the started state — first value may not arrive until the next dispatch. Imperceptible for screen state, but noticeable for high-frequency animations.

**How to apply it.**
```kotlin
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyScreen(viewModel: MyViewModel = koinViewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val pagingItems = viewModel.items.collectAsLazyPagingItems()
  // ...
}
```

**Seen in.**
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreen.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeScreen.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameScreen.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysScreen.kt

## What we don't do

- **No global state hoisting to the Activity / app root.** Every feature screen owns its ViewModel and its primary state boundary. **Why we avoid it:** global state defeats the modularization that lets features build and test independently.
- **No callback chains for state updates.** There is no `onDataChanged: (T) -> Unit` parameter threading. The ViewModel offers intent-driven methods (`loadDealDetails()`, `searchGames()`, `reloadGiveaways()`), and the Composable invokes them on user action. **Why we avoid it:** callbacks create implicit data flow that's hard to trace; intent methods make the ViewModel API the single source of truth for what the UI can do.
- **No reactive state on domain models.** Domain models are immutable data classes; reactive flows live only in the UI layer (ViewModel), never in domain or data. **Why we avoid it:** mixing reactive state into domain models couples persistence and UI lifecycle in places where it shouldn't.
- **No global event bus or inter-ViewModel messaging.** Inter-screen communication goes through navigation arguments or `SavedStateHandle` results. **Why we avoid it:** event buses make navigation logic untrackable.
- **No `stateIn(Eagerly)` for screen state.** `WhileSubscribed(5000)` is the default. **Why we avoid it:** screen-scoped flows shouldn't run when the screen isn't on screen.
