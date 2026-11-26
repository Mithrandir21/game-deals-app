---
**Path scope:** `logging/**`, `feature/**`, `domain/**`, `remote/**`, `common/**`, `app/**`
**Last surveyed:** 34b01013 on 2026-05-18
---

# Observability

Observability is a first-class concern. The app has a dedicated `:logging` module with a pluggable listener architecture, intentional error-handling boundaries, and structured Flow-based logging throughout. Sentry-KMP is wired on Android as a remote sink (breadcrumbs for INFO–WARN, exception/message capture for ERROR–FATAL); the iOS Sentry actual is pending SPM wiring of Sentry-Cocoa, so iOS currently sinks to NSLog only. Fatal exceptions are logged locally and forwarded to Sentry on Android; the listener architecture remains the seam for adding further remote sinks.

## Patterns

### Pluggable Logger Listener Architecture

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every log call in the app

**The pattern.**
`Logger` is an interface with two operations: `log(level, tag, throwable, messageProvider)` and `fatalThrowable()`. The `LoggingInterface` listener contract lives in commonMain; platform-specific implementations live under androidMain and iosMain. Multiple listeners can be registered at runtime via `addLoggerListener()` / `removeLoggerListener()`. `LoggerImpl` filters listeners by `isEnabled()` before dispatching. Android wires `SimpleLoggingListener` + `SentryLoggingListener`; iOS wires `IosConsoleLoggingListener`. The architecture allows stacking additional sinks (file logger, additional remote telemetry) without touching call sites.

**Why this works for us.**
Decouples logging policy from domain logic. Tests inject a `TestingLoggingListener` for verification. New listeners drop into Koin DI without changing anything else.

**Known trade-offs / when it strains.**
The listener set is mutable and accessed on every log call; logging on a hot path could contend. No per-listener level filtering — listeners are all-or-nothing via `isEnabled()`. Dispatch is synchronous; a slow listener slows the caller.

**How to apply it.**
```kotlin
val loggingAndroidModule = module {
  single<Logger> {
    LoggerImpl(
      mutableSetOf(
        SimpleLoggingListener(),
        SentryLoggingListener()
      )
    )
  }
}

val loggingIosModule = module {
  single<Logger> {
    LoggerImpl(mutableSetOf(IosConsoleLoggingListener()))
  }
}
```

**Seen in.**
- logging/src/commonMain/kotlin/pm/bam/gamedeals/logging/Logger.kt
- logging/src/commonMain/kotlin/pm/bam/gamedeals/logging/LoggerImpl.kt
- logging/src/commonMain/kotlin/pm/bam/gamedeals/logging/LoggingInterface.kt
- logging/src/androidMain/kotlin/pm/bam/gamedeals/logging/di/LoggingModule.kt
- logging/src/androidMain/kotlin/pm/bam/gamedeals/logging/implementations/SimpleLoggingListener.kt
- logging/src/androidMain/kotlin/pm/bam/gamedeals/logging/implementations/SentryLoggingListener.kt
- logging/src/iosMain/kotlin/pm/bam/gamedeals/logging/implementations/IosConsoleLoggingListener.kt

### Extension Functions as Call-Site Syntax

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** ~all log calls in features and domain

**The pattern.**
`Logger` is rarely called directly. Top-level extension functions on `Any` (`verbose()`, `debug()`, `info()`, `warn()`, `error()`, `fatal()`) infer the tag from the caller's `simpleName` unless overridden. Messages are lambda-deferred (`messageProvider: () -> String`), avoiding string allocation when the listener is disabled.

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
- logging/src/commonMain/kotlin/pm/bam/gamedeals/logging/Logger.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt

### Structured Flow Error Boundary with Logging

**Status:** emerging
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
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
- common/src/commonMain/kotlin/pm/bam/gamedeals/common/CommonFlowExtensions.kt
- common/src/commonMain/kotlin/pm/bam/gamedeals/common/FlowExtensions.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt

### Remote API Response Logging via Sandwich Wrapper

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every CheapShark / GamerPower API call

**The pattern.**
The Sandwich-Ktor library wraps Ktor responses in `ApiResponse<T>`. An extension `ApiResponse<T>.log(logger, level, tag)` invokes lambdas inside `onSuccess { … }`, `onError { … }`, and `onException { … }`, each logging a contextual message. After logging, the response flows through unchanged to subsequent operators (`mapAnyFailure`, `getOrThrow`).

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
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/Extensions.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt

### Fatal Exception Boundary in Coroutines

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** paging mediators and ViewModel/controller side effects

**The pattern.**
Error handlers in coroutines explicitly check for `CancellationException` and rethrow it; all other exceptions are logged via `fatal(logger, exception)` before being mapped to a result/state. This preserves structured concurrency (cancellation propagates) while ensuring unexpected exceptions leave a trace before the coroutine ends.

**Why this works for us.**
Prevents silent failures in paging loads or background operations. Structured concurrency is never broken. On Android, `fatal()` entries reach Sentry automatically via `SentryLoggingListener` — no per-call wiring required.

**Known trade-offs / when it strains.**
Requires manual try/catch in every coroutine block. iOS has no remote sink yet, so fatal entries on iOS land only in NSLog. There is no context-aware retry; fatal is final.

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
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/DealDetailsController.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt

### Sentry-KMP Integration as Remote Sink

**Status:** established (Android-only)
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every log call on Android (via the `LoggingInterface` seam)

**The pattern.**
`SentryLoggingListener` implements `LoggingInterface`. VERBOSE–WARN events become Sentry breadcrumbs via `Sentry.addBreadcrumb(...)`. ERROR–FATAL events become captures: fatal calls use `Sentry.captureException(throwable) { it.level = FATAL }`; non-fatal errors capture either the exception (when present) or the message. Sentry is initialized off-Main in `GameDealsApplication.initSentry()` via an IO dispatch so app startup is not blocked. The listener is registered in `loggingAndroidModule` and joins the pluggable Logger listener set on Android only.

**Why this works for us.**
Integrates with the existing `LoggingInterface` seam — no call-site changes. Off-Main init avoids startup hiccup. Per-level breadcrumb-vs-capture mapping keeps the Sentry dashboard signal-rich (low-severity entries become context for higher-severity captures rather than noise on the issues list).

**Known trade-offs / when it strains.**
Android-only; iOS still uses `IosConsoleLoggingListener` (NSLog) — the Sentry-KMP iOS variant needs SPM/Cocoa wiring to link Sentry-Cocoa, deliberately skipped for now. No retry on failed capture; Sentry's own buffering is relied on. Breadcrumb cardinality is not capped at the listener — relies on upstream call volume staying reasonable.

**How to apply it.**
```kotlin
class SentryLoggingListener : LoggingInterface {
  override fun isEnabled() = true

  override fun log(level: LogLevel, tag: String?, throwable: Throwable?, message: String) {
    when (level) {
      VERBOSE, DEBUG, INFO, WARN ->
        Sentry.addBreadcrumb(Breadcrumb().apply {
          this.level = level.toSentryLevel()
          this.category = tag
          this.message = message
        })
      ERROR ->
        throwable?.let { Sentry.captureException(it) } ?: Sentry.captureMessage(message)
      FATAL ->
        Sentry.captureException(throwable ?: RuntimeException(message)) { it.level = FATAL }
    }
  }
}
```

**Seen in.**
- logging/src/androidMain/kotlin/pm/bam/gamedeals/logging/implementations/SentryLoggingListener.kt
- logging/src/androidMain/kotlin/pm/bam/gamedeals/logging/di/LoggingModule.kt
- app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt

**Related lessons.** L-2026-05-05-04

**Tags.** `sentry`, `observability`, `kmp`

### Platform-Specific Logger Implementations

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every log call on every platform

**The pattern.**
Each platform ships a `LoggingInterface` implementation that fulfils the shared `Logger` contract declared in commonMain. Android: `SimpleLoggingListener` routes to `android.util.Log.*` (Logcat). iOS: `IosConsoleLoggingListener` routes to a helper `iosLog(message)` that calls `NSLog` and **escapes `%` characters first** — NSLog treats `%` as a printf format specifier, so a raw `%` in the message would crash with a malformed-format error. Tag is inferred from `simpleName` in both impls.

**Why this works for us.**
Shared `Logger` interface in commonMain; platform impls handle the rest. No conditional `if (isAndroid)` branching in calling code. Each impl is small and platform-idiomatic.

**Known trade-offs / when it strains.**
iOS escape rules need to be remembered when adding new sink types or formatters — anything writing to NSLog must apply the same `%` escape. Tested via listener tests in `logging/src/iosTest/`.

**How to apply it.**
```kotlin
// commonMain
interface LoggingInterface {
  fun isEnabled(): Boolean
  fun log(level: LogLevel, tag: String?, throwable: Throwable?, message: String)
}

// androidMain
class SimpleLoggingListener : LoggingInterface {
  override fun log(level: LogLevel, tag: String?, throwable: Throwable?, message: String) {
    when (level) { DEBUG -> Log.d(tag, message, throwable); /* … */ }
  }
}

// iosMain
class IosConsoleLoggingListener : LoggingInterface {
  override fun log(level: LogLevel, tag: String?, throwable: Throwable?, message: String) {
    iosLog("[$level] [${tag ?: "-"}] $message")
  }
}

fun iosLog(message: String) {
  val escaped = message.replace("%", "%%")
  NSLog(escaped)
}
```

**Seen in.**
- logging/src/androidMain/kotlin/pm/bam/gamedeals/logging/implementations/SimpleLoggingListener.kt
- logging/src/iosMain/kotlin/pm/bam/gamedeals/logging/implementations/IosConsoleLoggingListener.kt
- logging/src/iosMain/kotlin/pm/bam/gamedeals/logging/implementations/IosNsLog.kt

**Related lessons.** L-2026-05-04-06

**Tags.** `observability`, `kmp`, `logging`

### Ktor Client Logging via `expect`/`actual` `KtorPlatformLogger`

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** the shared Ktor client used by every remote source

**The pattern.**
Ktor's `Logging` plugin needs a `Logger` instance. The project's commonMain declares `expect val ktorPlatformLogger: Logger`. The Android `actual` is `KtorLogcatLogger` — an `object` writing to Logcat with a `Ktor` tag. The iOS `actual` is an anonymous `Logger` whose `log(message)` calls the `iosLog("[Ktor] $message")` helper. This avoids Ktor's SLF4J no-op default on JVM (which would silently swallow logs) and the runtime-error default on Native (no SLF4J equivalent).

**Why this works for us.**
Per-platform routing without conditional plugin install; the `Logging { logger = ktorPlatformLogger }` line in `gameDealsHttpClient(...)` stays platform-agnostic. iOS reuses the same NSLog escape path as the rest of iOS logging.

**Known trade-offs / when it strains.**
Another `expect`/`actual` pair to keep in sync. Minor — both impls are trivial and rarely change. No level filtering at the Ktor logger — relies on the plugin's `LogLevel` setting.

**How to apply it.**
```kotlin
// commonMain
expect val ktorPlatformLogger: io.ktor.client.plugins.logging.Logger

// androidMain
actual val ktorPlatformLogger = KtorLogcatLogger
object KtorLogcatLogger : Logger {
  override fun log(message: String) { Log.d("Ktor", message) }
}

// iosMain
actual val ktorPlatformLogger = object : Logger {
  override fun log(message: String) { iosLog("[Ktor] $message") }
}

// shared client wiring
HttpClient { install(Logging) { logger = ktorPlatformLogger; level = LogLevel.INFO } }
```

**Seen in.**
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/KtorPlatformLogger.kt
- remote/src/androidMain/kotlin/pm/bam/gamedeals/remote/logic/KtorPlatformLogger.android.kt
- remote/src/iosMain/kotlin/pm/bam/gamedeals/remote/logic/KtorPlatformLogger.ios.kt
- remote/src/androidMain/kotlin/pm/bam/gamedeals/remote/logic/KtorLogcatLogger.kt

**Tags.** `ktor`, `observability`, `kmp`

## What we don't do

- **No iOS Sentry listener yet.** `IosConsoleLoggingListener` (NSLog) is the only iOS sink. **Why we avoid it:** Sentry-KMP's iOS variant needs SPM wiring of Sentry-Cocoa, which is parked behind broader iOS packaging work. The Android listener proves the pattern; the iOS actual slots in when SPM is wired.
- **No `BuildConfig.DEBUG` conditional logging.** `SimpleLoggingListener` always logs at all levels. **Why we avoid it:** uniform behavior across builds keeps issue reproduction predictable. Listener replacement (not branching) is the seam for variant-specific logging.
- **No distributed tracing or trace-ID propagation across Flows.** Errors are logged in isolation; no correlation across Flows or services.
- **No log buffering / batching.** Logging is synchronous and immediate. **Why we avoid it:** at current call volumes the overhead is negligible; batching would complicate ordering guarantees and interact badly with Sentry's own buffering.
- **No per-listener level filtering.** Listeners are all-or-nothing via `isEnabled()`; level routing is the listener's own responsibility (see `SentryLoggingListener`'s breadcrumb-vs-capture split).
- **No scoped or context-aware metadata** (request IDs, user IDs in ThreadLocal). Tags are class names or custom strings only.
- **No metrics, counters, or gauges.** Logging is event-based only — no latency percentiles, call rates, or resource-usage tracking.

## Decommissioned

### Activity Lifecycle Logging via Base Class

**Status:** deprecated (`:base` module deleted during KMP migration 2026-05; lifecycle logging was Activity-scoped and is no longer load-bearing for a single-Activity Compose app — no replacement.)
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
