---
**Path scope:** `logging/**`, `base/**`, `feature/**`, `domain/**`, `remote/**`, `common/**`, `app/**`
**Last surveyed:** 31a89bc on 2026-05-03
---

# Observability

Observability is a first-class concern. The app has a dedicated `:logging` module with a pluggable listener architecture, intentional error-handling boundaries, and structured Flow-based logging throughout. Crash reporting (Crashlytics, Bugsnag) is not yet wired; Firebase Analytics is provided in DI but not actively consumed. Fatal exceptions are logged locally; the listener architecture is the seam for adding remote sinks.

## Patterns

### Pluggable Logger Listener Architecture

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** every log call in the app

**The pattern.**
`Logger` is an interface with two operations: `log(level, tag, throwable, messageProvider)` and `fatalThrowable()`. Multiple `LoggingInterface` listeners can be registered at runtime via `addLoggerListener()` / `removeLoggerListener()`. `LoggerImpl` filters listeners by `isEnabled()` before dispatching. Production wires only `SimpleLoggingListener` (Android `Log.*`); the architecture allows stacking (e.g., file logger, remote telemetry) without touching call sites.

**Why this works for us.**
Decouples logging policy from domain logic. Tests inject a `TestingLoggingListener` for verification. Future listeners (Crashlytics, analytics) drop into DI without changing anything else.

**Known trade-offs / when it strains.**
The listener set is mutable and accessed on every log call; logging on a hot path could contend. No per-listener level filtering — listeners are all-or-nothing via `isEnabled()`. Dispatch is synchronous; a slow listener slows the caller.

**How to apply it.**
```kotlin
@Provides
@Singleton
fun provideLogger(
  crashlytics: FirebaseCrashlytics,
  analytics: FirebaseAnalytics
): Logger = LoggerImpl(
  mutableSetOf(
    SimpleLoggingListener(),
    CrashlyticsLoggingListener(crashlytics),
    AnalyticsLoggingListener(analytics)
  )
)
```

**Seen in.**
- logging/src/main/java/pm/bam/gamedeals/logging/Logger.kt
- logging/src/main/java/pm/bam/gamedeals/logging/LoggerImpl.kt
- logging/src/main/java/pm/bam/gamedeals/logging/LoggingInterface.kt
- logging/src/main/java/pm/bam/gamedeals/logging/di/LoggingModule.kt
- logging/src/main/java/pm/bam/gamedeals/logging/implementations/SimpleLoggingListener.kt

### Extension Functions as Call-Site Syntax

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** ~all log calls in features and domain

**The pattern.**
`Logger` is rarely called directly. Top-level extension functions on `Any` (`verbose()`, `debug()`, `info()`, `warn()`, `error()`, `fatal()`) infer the tag from the caller's `javaClass.simpleName` unless overridden. Messages are lambda-deferred (`messageProvider: () -> String`), avoiding string allocation when the listener is disabled.

**Why this works for us.**
Syntax is lean: `debug(logger) { "loaded ${count} items" }` vs `logger.log(LogLevel.DEBUG, ...)`. Deferred messages prevent garbage in development. Class-name tagging is automatic and uniform.

**Known trade-offs / when it strains.**
Class-name tags can collide in large packages; manually overriding is verbose. A class that logs across many functions has the same tag everywhere — less distinguishing than a function-level constant.

**How to apply it.**
```kotlin
fun loadData() {
  debug(logger) { "Starting load" }                       // tag: ClassName
  error(logger, exception, tag = "loadData.error") { "Failed" }
  fatal(logger, exception)                                // infers tag, logs fatal
}
```

**Seen in.**
- logging/src/main/java/pm/bam/gamedeals/logging/Logger.kt
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/paging/DealsMediator.kt
- common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt

### Structured Flow Error Boundary with Logging

**Status:** emerging
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** repository and ViewModel Flow chains

**The pattern.**
Flows are wrapped with `logFlow()` (logs start, collect, success, completion) and chained with custom operators: `.onError { error(logger, it) { … } }`, `.retryOnException(logger, attempts = 3)` with per-attempt logging, `.catchAndContinue(fallback)`. Errors flow through with logged context before being caught or rethrown. `CancellationException` is always rethrown.

**Why this works for us.**
Every error in a data-loading flow is observed and logged at the point it occurs, without hiding it. Retry loops log attempt counts and backoff. The `logFlow()` wrapper standardizes start/success/failure capture without per-consumer boilerplate.

**Known trade-offs / when it strains.**
Multiple layers of `.onError` (in `logFlow`, then `.retryOnException`) can log the same error twice. No trace IDs to correlate errors across Flows. In debug mode, deep operator chains produce noisy logs.

**How to apply it.**
```kotlin
fun loadReleases(): Flow<List<Release>> =
  flow { emitAll(releasesRepository.observeReleases()) }
    .logFlow(logger, tag = "LoadReleases")
    .retryOnException(logger, attempts = 3) { cause, attempt ->
      debug(logger) { "Retry #$attempt: ${cause.message}" }
    }
    .catch { emit(emptyList()) }
```

**Seen in.**
- common/src/main/java/pm/bam/gamedeals/common/CommonFlowExtensions.kt
- common/src/main/java/pm/bam/gamedeals/common/FlowExtensions.kt
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt

### Activity Lifecycle Logging via Base Class

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** any Activity that extends `LoggingBaseActivity`

**The pattern.**
`LoggingBaseActivity` overrides every lifecycle method and configuration-change hook, logging entry to each with the Activity's class name as tag and the method name as message. No filter — all callbacks log at default level.

**Why this works for us.**
Rapid troubleshooting of state-restoration bugs and orientation changes. Every lifecycle transition is timestamped by the logging system without per-Activity instrumentation.

**Known trade-offs / when it strains.**
Noisy in debug builds — every rotation fires 10+ log lines. Not selective by Activity; doesn't capture Fragment transitions or ViewModel state. Log level cannot be tuned per Activity or phase.

**How to apply it.**
```kotlin
@AndroidEntryPoint
abstract class LoggingBaseActivity : ComponentActivity() {
  @Inject lateinit var logger: Logger

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    logger.log(tag = this::class.simpleName) {
      "onCreate (savedInstanceState != null) = ${savedInstanceState != null}"
    }
  }
  // onStart, onResume, onPause, onDestroy, …
}
```

**Seen in.**
- base/src/main/java/pm/bam/gamedeals/base/LoggingBaseActivity.kt
- app/src/main/java/pm/bam/gamedeals/MainActivity.kt

### Remote API Response Logging via Sandwich Wrapper

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** every CheapShark / GamerPower API call

**The pattern.**
The Sandwich library wraps Retrofit responses in `ApiResponse<T>`. An extension `ApiResponse<T>.log(logger, level, tag)` invokes lambdas inside `onSuccess { … }`, `onError { … }`, and `onException { … }`, each logging a contextual message. After logging, the response flows through unchanged to subsequent operators (`mapAnyFailure`, `getOrThrow`).

**Why this works for us.**
One-liner logging per API call, no try/catch at call sites. Errors and successes have equal visibility. Default level (DEBUG) is tunable per call.

**Known trade-offs / when it strains.**
Logging `data` on success can be verbose for large responses. No structured fields (headers, timing). Layered with `mapAnyFailure`, errors can be logged twice — once by `log()` and again upstream.

**How to apply it.**
```kotlin
dealsApi.getDeals(storeID = storeId, …)
  .log(logger, tag = "DealsAPI")
  .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
  .getOrThrow()
  .map { it.toDeal(…) }
```

**Seen in.**
- remote/src/main/java/pm/bam/gamedeals/remote/logic/Extensions.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt

### Fatal Exception Boundary in Coroutines

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** paging mediators and ViewModel/controller side effects

**The pattern.**
Error handlers in coroutines explicitly check for `CancellationException` and rethrow it; all other exceptions are logged via `fatal(logger, exception)` before being mapped to a result/state. This preserves structured concurrency (cancellation propagates) while ensuring unexpected exceptions leave a trace before the coroutine ends.

**Why this works for us.**
Prevents silent failures in paging loads or background operations. Structured concurrency is never broken. Fatal entries appear locally even though Crashlytics is not yet wired — when it is, the listener architecture forwards them automatically.

**Known trade-offs / when it strains.**
Requires manual try/catch in every coroutine block. `fatal()` doesn't auto-propagate to crash reporters until a listener is wired. There is no context-aware retry; fatal is final.

**How to apply it.**
```kotlin
override suspend fun load(loadType: LoadType, state: PagingState<…>): MediatorResult {
  try {
    // … do work
    return MediatorResult.Success(…)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    fatal(logger, e)
    return MediatorResult.Error(e)
  }
}
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/paging/DealsMediator.kt
- common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt

## What we don't do

- **No crash reporting in production code.** Firebase Analytics is provided via DI but not used. Crashlytics / Bugsnag are not integrated. **Why we avoid it:** the listener architecture is ready; crash reporting is not yet a project priority. When wired, it slots in as another listener.
- **No `BuildConfig.DEBUG` conditional logging.** `SimpleLoggingListener` always logs at all levels. **Why we avoid it:** uniform behavior across builds keeps issue reproduction predictable. Listener replacement (not branching) is the seam for variant-specific logging.
- **No distributed tracing or trace IDs.** Errors are logged in isolation; no correlation across Flows or services.
- **No async batching or log buffering.** Logging is synchronous and immediate. **Why we avoid it:** at current call volumes the overhead is negligible; batching would complicate ordering guarantees.
- **No scoped or context-aware metadata** (request IDs, user IDs in ThreadLocal). Tags are class names or custom strings only.
- **No metrics, counters, or gauges.** Logging is event-based only — no latency percentiles, call rates, or resource-usage tracking.
