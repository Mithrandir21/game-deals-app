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
