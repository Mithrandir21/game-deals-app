| Field | Value |
|---|---|
| Severity | Low |
| Category | KMP / iOS concurrency |
| Location | `common/src/iosMain/kotlin/pm/bam/gamedeals/common/datetime/formatting/PlatformDateFormatter.ios.kt:13-20` |
| Effort | Trivial |
| Confidence | Low |

**Description.** `iosFormatter` is a top-level `private val` holding a single `NSDateFormatter` instance configured once via `apply { dateFormat = ...; locale = ...; timeZone = ... }`. Every call to `formatLocaleAwareDate(instant)` reuses the same object. The formatter is invoked from CheapShark response mappers (`DealMappers.kt:37/61/85`, `GameMappers.kt:41/58`) which run on whatever dispatcher Ktor's Darwin engine parks the response on.

**Impact.** Safe today by Apple's documented thread-safety contract for `NSDateFormatter` under `NSDateFormatterBehavior10_4` (default since iOS 7), as long as no caller reconfigures the formatter after init. The pattern becomes unsafe the moment someone adds locale switching at runtime or reuses the formatter for parsing. `NSDateFormatter` is a famous foot-gun — flagged so a future reconfiguring change is noticed.

**Evidence.**
```kotlin
private val iosFormatter = NSDateFormatter().apply {
    dateFormat = "MMM dd, yyyy"
    locale = NSLocale.currentLocale
    timeZone = NSTimeZone.systemTimeZone
}

internal actual fun formatLocaleAwareDate(instant: Instant): String =
    iosFormatter.stringFromDate(instant.toNSDate())
```

**Recommended fix.** Either (1) instantiate the formatter inside `formatLocaleAwareDate` (cheap on iOS for purely formatting use), or (2) add a comment documenting the thread-safety assumption so a future change doesn't quietly add a mutator.

**Confidence rationale.** Low because Apple's documented behavior says this is safe today. Flagged because it's the kind of code that becomes unsafe the moment someone adds locale switching or reusing for parsing.
