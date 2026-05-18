---
**Path scope:** `domain/**`, `remote/**`, `feature/**`, `common/**`
**Last surveyed:** 34b01013 on 2026-05-18
---

# Errors

The error story spans three layers: **sealed exception types at remote boundaries**, **catch-and-emit in ViewModels**, and **sealed screen states** in the UI for graceful degradation.

## Patterns

### Sealed `RemoteHttpException` at the Remote Boundary

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** core remote module + both API sources

**The pattern.**
The remote module defines a sealed `RemoteHttpException` that encodes specific HTTP codes (400, 401, 403, 404, 405) as singleton variants, plus a catch-all `HttpException(code: Int)`. Ktor's `ResponseException` is transformed into this boundary type via `RemoteExceptionTransformerImpl`, with a `ResponseException.toRemoteHttpException()` extension acting as the bridge. Domain and feature modules never depend on Ktor — they only ever see `RemoteHttpException` (or other thrown exceptions). Transformation is invoked through a `mapAnyFailure` extension on Sandwich-Ktor's `ApiResponse`, which applies logging and converts the failure in one pass before `getOrThrow()` exposes it.

**Why this works for us.**
Ktor dependencies stay isolated in `:remote`. Modules above receive only `RemoteHttpException` (or other thrown exceptions), keeping the dependency graph clean and making error scenarios testable without mocking HTTP libraries.

**Known trade-offs / when it strains.**
Not exhaustively typed — there's still a catch-all `HttpException(code)` for unmapped codes. New status codes need explicit variants if they want special handling.

**How to apply it.**
```kotlin
sealed class RemoteHttpException(open val code: Int) : RuntimeException() {
  data object BadRequest : RemoteHttpException(400)
  data object NotFound : RemoteHttpException(404)
  data class HttpException(override val code: Int) : RemoteHttpException(code)
}

fun ResponseException.toRemoteHttpException(): RemoteHttpException =
  when (response.status.value) {
    400 -> RemoteHttpException.BadRequest
    404 -> RemoteHttpException.NotFound
    else -> RemoteHttpException.HttpException(response.status.value)
  }

fun <T> ApiResponse<T>.mapAnyFailure(
  transformer: Throwable.() -> Throwable
): ApiResponse<T> = when (this) {
  is ApiResponse.Failure.Exception -> ApiResponse.exception(ex = transformer(throwable))
  else -> this
}
```

**Seen in.**
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/exceptions/RemoteExternalExceptions.kt
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/exceptions/RemoteExceptionTransformer.kt
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/Extensions.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt
- remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/GamerPowerSourceImpl.kt

### Sealed Screen State with `Error` Variant

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
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
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt

### `Flow.catch` with State Emission

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
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
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt

### Try/Catch with State Emission in Controllers (Re-throw `CancellationException`)

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
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
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt

### Exceptions Bubble Repository → ViewModel; UI Layer Catches

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
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
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/games/GamesRepository.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt

### Flow Logging via `.logFlow()` Extension

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
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
- common/src/commonMain/kotlin/pm/bam/gamedeals/common/CommonFlowExtensions.kt
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/Extensions.kt

### Retry via Reload Triggers (User-Initiated)

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
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
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt

### API Classes Wrap Calls in Try/Catch → `ApiResponse.exception(t)`

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all Ktor-backed API classes in `:remote`

**The pattern.**
Each API method body is a try/catch that wraps the underlying Ktor call. Success returns `ApiResponse.Success(...)`; any non-`CancellationException` `Throwable` returns `ApiResponse.exception(t)`. `CancellationException` is re-thrown to preserve structured concurrency. The resulting `ApiResponse` flows through `.log() → .mapAnyFailure() → .getOrThrow()` in the source impl, where the failure is transformed into a `RemoteHttpException` before reaching domain code.

**Why this works for us.**
Ktor (unlike Retrofit) doesn't ship a call adapter that wraps responses into a result type. Doing the wrap once per call site keeps the result shape consistent with the rest of the chain and the existing Sandwich-based extensions still apply without modification.

**Known trade-offs / when it strains.**
Boilerplate at every API method. If the count grows, a small `tryCatch` helper that does the `CancellationException` re-throw can absorb the noise without changing semantics.

**How to apply it.**
```kotlin
suspend fun getDeals(storeId: Int): ApiResponse<List<DealResponse>> =
  try {
    val response = httpClient.get("/deals") { parameter("storeID", storeId) }
    ApiResponse.Success(response.body())
  } catch (t: CancellationException) {
    throw t
  } catch (t: Throwable) {
    ApiResponse.exception(t)
  }
```

**Seen in.**
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/api/DealsApi.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/api/GamesApi.kt
- remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/api/GamesApi.kt

### Ktor `expectSuccess = true` + `HttpTimeout` at Client Init

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every `HttpClient` constructed in `:remote`

**The pattern.**
At `HttpClient` init, two flags are wired together: `expectSuccess = true` makes 4xx/5xx responses throw `ResponseException` (otherwise Ktor returns a successful response holder with a non-success status), and the `HttpTimeout` plugin sets `connectTimeoutMillis = 10_000` and `requestTimeoutMillis = 30_000`. Both exception classes (`ResponseException` and `HttpRequestTimeoutException`) flow through `RemoteExceptionTransformer.transformApiException()` and become `RemoteHttpException` variants (or stay generic for timeouts).

**Why this works for us.**
This aligns Ktor's behavior with the project's "errors are exceptions" model — there's no second code path for "successful response with bad status". Explicit timeouts avoid indefinite hangs in tests and on flaky networks, which previously had to be enforced via test rules.

**Known trade-offs / when it strains.**
Global timeouts apply to every endpoint; a slow but legitimate call (e.g., long-running export) needs a per-request override. `expectSuccess` means any non-2xx path raises — call sites can no longer inspect a status code without catching first.

**How to apply it.**
```kotlin
val httpClient = HttpClient(engine) {
  expectSuccess = true
  install(HttpTimeout) {
    connectTimeoutMillis = 10_000
    requestTimeoutMillis = 30_000
  }
  install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}
```

**Seen in.**
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/HttpClientFactory.kt

### Catch-All `HttpException(code)` for Unmapped Status Codes

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** `RemoteHttpException` hierarchy

**The pattern.**
The sealed `RemoteHttpException` hierarchy enumerates 400/401/403/404/405 as `data object` variants, then falls through to `HttpException(code: Int)` for everything else. The transformer's `when` block matches the common 4xx codes explicitly and routes anything else into the catch-all so a typed exception is always available regardless of status.

**Why this works for us.**
The transformer can return a typed exception for any status without forcing sealed-type changes when new status codes (typically 5xx) start appearing in production. The common 4xx set still gets the ergonomic `data object` treatment for `when`-exhaustive handling.

**Known trade-offs / when it strains.**
Callers that want to specifically handle e.g. 503 must match on `code` rather than relying on an exhaustive `when`. The per-status sealed types only cover the common 4xx set, so it's easy to forget to handle a particular 5xx until it shows up.

**How to apply it.**
```kotlin
sealed class RemoteHttpException(open val code: Int) : RuntimeException() {
  data object BadRequest : RemoteHttpException(400)
  data object Unauthorized : RemoteHttpException(401)
  data object Forbidden : RemoteHttpException(403)
  data object NotFound : RemoteHttpException(404)
  data object MethodNotAllowed : RemoteHttpException(405)
  data class HttpException(override val code: Int) : RemoteHttpException(code)
}

fun Int.toRemoteHttpException(): RemoteHttpException = when (this) {
  400 -> RemoteHttpException.BadRequest
  401 -> RemoteHttpException.Unauthorized
  403 -> RemoteHttpException.Forbidden
  404 -> RemoteHttpException.NotFound
  405 -> RemoteHttpException.MethodNotAllowed
  else -> RemoteHttpException.HttpException(this)
}
```

**Seen in.**
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/exceptions/RemoteExternalExceptions.kt
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/exceptions/RemoteExceptionTransformer.kt

## What we don't do

- **No `Result<Success, Failure>` sealed type.** Errors are modeled as thrown exceptions (bubbling from data sources) or as UI states (sealed in ViewModels). **Why we avoid it:** an extra `Result` layer would duplicate what the screen-state sealed types already encode for the UI.
- **No payload on UI error states.** Screen states have a single `Error` variant without `reason` or `code` data. **Why we avoid it:** all surfaced errors today are network-class; if richer messaging is needed later, the variant can grow.
- **No exception wrapping into custom domain types.** Most errors propagate as thrown HTTP exceptions or generic `Throwable`. The pre-existing `DataExistsException` / `DataNotFoundException` types in `:common` are rarely thrown.
- **No structured retry (exponential backoff, circuit breakers).** Retry is manual and user-initiated.
- **No automatic snackbar/Toast as a safety net.** If a screen state is `Error`, the UI is responsible for rendering it; there's no global error layer.
