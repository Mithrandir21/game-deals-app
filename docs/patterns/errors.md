---
**Path scope:** `domain/**`, `remote/**`, `feature/**`, `common/**`
**Last surveyed:** 31a89bc on 2026-05-03
---

# Errors

The error story spans three layers: **sealed exception types at remote boundaries**, **catch-and-emit in ViewModels**, and **sealed screen states** in the UI for graceful degradation.

## Patterns

### Sealed `RemoteHttpException` at the Remote Boundary

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** core remote module + both API sources

**The pattern.**
The remote module defines a sealed `RemoteHttpException` that encodes specific HTTP codes (400, 401, 403, 404, 405) as singleton variants, plus a catch-all `HttpException(code: Int)`. Retrofit's `HttpException` is transformed into this boundary type via `RemoteExceptionTransformerImpl`, so domain and feature modules never depend on Retrofit. Transformation is invoked through a `mapAnyFailure` extension on Sandwich's `ApiResponse`, which also applies logging and converts the failure in one pass before `getOrThrow()` exposes it.

**Why this works for us.**
Retrofit dependencies stay isolated in `:remote`. Modules above receive only `RemoteHttpException` (or other thrown exceptions), keeping the dependency graph clean and making error scenarios testable without mocking HTTP libraries.

**Known trade-offs / when it strains.**
Not exhaustively typed — there's still a catch-all `HttpException(code)` for unmapped codes. New status codes need explicit variants if they want special handling.

**How to apply it.**
```kotlin
sealed class RemoteHttpException(open val code: Int) : RuntimeException() {
  data object BadRequest : RemoteHttpException(400)
  data object NotFound : RemoteHttpException(404)
  data class HttpException(override val code: Int) : RemoteHttpException(code)
}

fun <T> ApiResponse<T>.mapAnyFailure(
  transformer: Throwable.() -> Throwable
): ApiResponse<T> = when (this) {
  is ApiResponse.Failure.Error -> ApiResponse.exception(ex = statusCode.code.toRemoteHttpException())
  is ApiResponse.Failure.Exception -> ApiResponse.exception(ex = transformer(throwable))
  else -> this
}
```

**Seen in.**
- remote/src/main/java/pm/bam/gamedeals/remote/exceptions/RemoteExternalExceptions.kt
- remote/src/main/java/pm/bam/gamedeals/remote/logic/Extensions.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt
- remote/gamerpower/src/main/java/pm/bam/gamedeals/remote/gamerpower/GamerPowerSourceImpl.kt

### Sealed Screen State with `Error` Variant

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all feature ViewModels

**The pattern.**
Each ViewModel defines a sealed type for its screen state with at least three variants: `Loading`, `Error`, and `Data`/`Success`. When a Flow chain encounters an error, it catches and emits an `Error` state instead of letting the exception bubble to the UI. The UI then `when`-matches on the sealed state and renders accordingly.

**Why this works for us.**
The UI is never left in a crashed or unrenderable state. Errors are part of the contract. ViewModels expose stable `StateFlow<ScreenState>` that the UI reliably binds to, with no need for separate error event flows.

**Known trade-offs / when it strains.**
The `Error` variant carries no detail about *why* loading failed. To show different error messages for different failure modes, the variant must carry distinguishing data — currently uniform.

**How to apply it.**
```kotlin
sealed class StoreScreenData {
  data object Loading : StoreScreenData()
  data object Error : StoreScreenData()
  data class Data(val store: Store) : StoreScreenData()
}

val uiState = storeIdFlow
  .flatMapLatest { id ->
    flowOf(id)
      .map<Int, StoreScreenData> { StoreScreenData.Data(storesRepository.getStore(it)) }
      .catch { emit(StoreScreenData.Error) }
      .onStart { emit(StoreScreenData.Loading) }
  }
  .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), StoreScreenData.Loading)
```

**Seen in.**
- feature/store/src/main/java/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt
- feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt
- feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt

### `Flow.catch` with State Emission

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** Home, Game, Search, Store ViewModels

**The pattern.**
Coroutine Flows are decorated with `.catch { emit(...) }` to intercept exceptions and emit a corresponding state — usually `ERROR` or empty. The catch block does not re-throw; the chain completes gracefully even if an intermediate step throws. Logs are emitted at error points via `.onError { warn(logger, it) }` or `.logFlow()`.

**Why this works for us.**
The declarative chain stays predictable and testable. An error doesn't crash the ViewModel; the UI transitions to an error state and can offer retry.

**Known trade-offs / when it strains.**
Once `.catch { }` emits, the Flow is complete — it doesn't retry by default. Explicit retry logic must be layered if the user expects "tap to retry".

**How to apply it.**
```kotlin
loadTopStoreDataFlow()
  .onStart { _uiState.update { it.copy(state = HomeScreenStatus.LOADING) } }
  .logFlow(logger)
  .catch { emit(HomeScreenData(state = HomeScreenStatus.ERROR)) }
  .collect { _uiState.emit(it) }
```

**Seen in.**
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/store/src/main/java/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt
- feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt
- feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt

### Try/Catch with State Emission in Controllers (Re-throw `CancellationException`)

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** common UI controllers (`DealDetailsController`)

**The pattern.**
Controllers like `DealDetailsController` use `try/catch` at the launch-scope level rather than Flow operators. On success, a `Data` state is emitted; on exception, an `Error` state. `CancellationException` is always re-thrown to preserve cancellation semantics. The caught exception is logged at `fatal` level. If even the error-state emission fails, the state flow is set to `null` as a last resort.

**Why this works for us.**
Controllers orchestrating multiple suspend calls benefit from try/catch for linear readability. Errors surface naturally without Flow-operator chaining; cancellation propagates correctly.

**Known trade-offs / when it strains.**
Nested try/catch (main logic + error fallback) is verbose. If both error emission and fallback fail, state becomes `null` rather than a well-defined error state.

**How to apply it.**
```kotlin
scope.launch {
  try {
    _dealDetails.emit(DealBottomSheetData.DealDetailsLoading(...))
    val data = withMinimumDuration(750L) { dealsRepository.getDeal(dealId) }
    _dealDetails.emit(toData(data))
  } catch (t: CancellationException) {
    throw t
  } catch (t: Throwable) {
    fatal(logger, t)
    try { _dealDetails.emit(DealBottomSheetData.DealDetailsError(...)) }
    catch (inner: Throwable) {
      fatal(logger, inner)
      _dealDetails.emit(null)
    }
  }
}
```

**Seen in.**
- common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt

### Exceptions Bubble Repository → ViewModel; UI Layer Catches

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all data sources and repositories

**The pattern.**
Repositories expose suspend functions that do not catch or wrap exceptions. They call API sources, which transform HTTP errors to `RemoteHttpException` and re-throw on failure. Repositories propagate exceptions upward unchanged. The ViewModel handles them in its Flow chain or try/catch.

**Why this works for us.**
Repositories stay simple and don't make UI-specific decisions. Each layer decides its own error handling. Easy to test: mock a repository to throw and verify the ViewModel emits an error state.

**Known trade-offs / when it strains.**
Every consumer must remember to catch. There's no implicit fallback or retry in the repository.

**How to apply it.**
```kotlin
// Repository — no error handling
suspend fun getStoreDeals(storeId: Int): List<Deal> =
  dealsApi.getDeals(...)
    .mapAnyFailure { ... }
    .getOrThrow()

// ViewModel — catches and handles
private fun loadStoreDataFlow() = flowOf(storeId)
  .flatMapLatest { dealsRepository.getStoreDeals(it) }
  .catch { emit(emptyList()) }
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/games/GamesRepository.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt

### Flow Logging via `.logFlow()` Extension

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** Flow chains across feature modules

**The pattern.**
A shared `.logFlow(logger)` extension wraps Flows with `onEach` / `onError` / `onStart` / `onCompletion` callbacks that log all state transitions. Errors log at `warn` or `error` level. A companion `.onError { }` extension allows downstream operators to register additional error handlers without catching. Logging happens in the operator chain, not in the catch block.

**Why this works for us.**
Error visibility is uniform — every Flow that goes through `.logFlow()` automatically logs failures. Callers can hook `.onError { }` for custom side effects (e.g., analytics) without restructuring the chain.

**Known trade-offs / when it strains.**
Errors are logged but not captured in the emit sequence. To make the UI react to a logged error, you must additionally `.catch { }` and emit a state.

**How to apply it.**
```kotlin
loadTopStoreDataFlow()
  .logFlow(logger)
  .catch { emit(HomeScreenData(state = HomeScreenStatus.ERROR)) }
  .collect { ... }
```

**Seen in.**
- common/src/main/java/pm/bam/gamedeals/common/CommonFlowExtensions.kt
- remote/src/main/java/pm/bam/gamedeals/remote/logic/Extensions.kt

### Retry via Reload Triggers (User-Initiated)

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** Game, Store ViewModels

**The pattern.**
ViewModels expose reload functions (`reloadGameDetails()`, etc.) that emit on a `MutableSharedFlow` retry trigger. The trigger is combined with the data-loading Flow chain via `combine + flatMapLatest`, so each emission causes the chain to re-execute. The UI calls the reload function on a "retry" tap.

**Why this works for us.**
Retry is user-initiated and explicit. The ViewModel doesn't retry automatically (which could hammer the API or loop infinitely). The reload mechanism is simple and composable.

**Known trade-offs / when it strains.**
No backoff or progressive delay. Each tap immediately re-fetches. For transient errors, the user may need to tap multiple times.

**How to apply it.**
```kotlin
private val reloadTrigger = MutableSharedFlow<Unit>(
  replay = 0, extraBufferCapacity = 1,
  onBufferOverflow = BufferOverflow.DROP_OLDEST
)

init {
  viewModelScope.launch {
    combine(gameIdFlow, reloadTrigger.onStart { emit(Unit) }) { id, _ -> id }
      .flatMapLatest { loadGameDetailsFlow(it) }
      .collect { _uiState.emit(it) }
  }
}

fun reloadGameDetails() { reloadTrigger.tryEmit(Unit) }
```

**Seen in.**
- feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt

## What we don't do

- **No `Result<Success, Failure>` sealed type.** Errors are modeled as thrown exceptions (bubbling from data sources) or as UI states (sealed in ViewModels). **Why we avoid it:** an extra `Result` layer would duplicate what the screen-state sealed types already encode for the UI.
- **No payload on UI error states.** Screen states have a single `Error` variant without `reason` or `code` data. **Why we avoid it:** all surfaced errors today are network-class; if richer messaging is needed later, the variant can grow.
- **No exception wrapping into custom domain types.** Most errors propagate as thrown HTTP exceptions or generic `Throwable`. The pre-existing `DataExistsException` / `DataNotFoundException` types in `:common` are rarely thrown.
- **No structured retry (exponential backoff, circuit breakers).** Retry is manual and user-initiated.
- **No automatic snackbar/Toast as a safety net.** If a screen state is `Error`, the UI is responsible for rendering it; there's no global error layer.
