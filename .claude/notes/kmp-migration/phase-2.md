# Phase 2 — `java.time` → `kotlinx-datetime` migration

**Branch:** `feature/kmp-migration-phase-2-domain`
**Started:** 2026-05-03
**Scope:** **Reduced from the original plan.** Datetime swap only (the original Phase 2's "2A"). The plan's "`:domain` becomes a KMP module + Room KMP" half (the original "2B") is deferred and folded into Phase 5, alongside the Paging Multiplatform swap that gates the Room source migration anyway.

## What was done

### Datetime swap in `:common`
- `kotlinx-datetime` added to commonMain dependencies.
- `DatetimeParsing` interface and `DatetimeParsingImpl` move from `androidMain/` to `commonMain/`. Body rewritten using `kotlinx.datetime.LocalDateTime` and the format builder (`LocalDateTime.Format { year(); ... }`) for the `"yyyy-MM-dd HH:mm:ss"` pattern.
- `DateTimeFormatter` interface and `DateTimeFormatterImpl` move from `androidMain/` to `commonMain/`. The locale-aware `"MMM dd, yyyy"` format goes through a new `internal expect fun formatLocaleAwareDate(Instant): String`. kotlinx-datetime's format builder cannot yet produce locale-aware month abbreviations; expect/actual delegates to platform-native formatters.
  - **Android actual** (`androidMain/.../PlatformDateFormatter.android.kt`): `java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")` with `Locale.getDefault()` and `ZoneId.systemDefault()`.
  - **iOS actual** (`iosMain/.../PlatformDateFormatter.ios.kt`): `NSDateFormatter` with `dateFormat = "MMM dd, yyyy"`, `locale = NSLocale.currentLocale`, `timeZone = NSTimeZone.systemTimeZone`.
- Old `androidMain/.../datetime/*` files deleted.

### Consumer ripple (16 files surveyed; 7 actually modified)
- `:domain/utils/TypeAdapters.kt` — `LocalDatetimeConverter` body rewritten using `kotlinx.datetime` + `TimeZone.UTC`.
- `:domain/utils/TypeSerializers.kt` — `LocalDateSerializer` declares its `descriptor` explicitly (`PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.LONG)`). The previous `@Serializer(forClass = LocalDateTime::class)` annotation requires the target class to live in the same module; `kotlinx.datetime.LocalDateTime` doesn't.
- `:domain/models/Giveaway.kt` — import swap.
- `:common:ui/PreviewData.kt` — `LocalDateTime.now()` → concrete `LocalDateTime(2026, 1, 1, 0, 0)` (preview data should be deterministic anyway).
- `DatetimeParsingImplTest.kt`, `DateTimeFormatterImplTest.kt` — `Instant.ofEpochSecond()` → `Instant.fromEpochSeconds()`; `LocalDateTime.of(2026, Month.JANUARY, ...)` → `LocalDateTime(2026, 1, ...)`.
- `GiveawaysRepositoryTest.kt` — `LocalDateTime.MIN/MAX` (kotlinx-datetime doesn't have them) replaced with concrete sentinels (`LocalDateTime(1970, 1, 1, 0, 0)` and `LocalDateTime(9999, 12, 31, 23, 59, 59)`).
- `GamerPowerSourceImplTest.kt` — `LocalDateTime.of(2026, 1, 1, 0, 0)` → `LocalDateTime(2026, 1, 1, 0, 0)`.

### Things that did *not* change
- The 6 `@Inject`-using domain classes (Hilt's `@Provides` was redundant with `@Inject` on `DateTimeFormatterImpl`/`DatetimeParsingImpl`, so dropping `@Inject` worked cleanly when those moved to commonMain. `LocalDateSerializer`, `LocalDatetimeConverter`, `StoreImagesConverter`, `GiveawayPlatformsConverter` keep `@Inject` — they're still in `:domain` androidMain.)
- All 7 `:remote:*` mapper files I'd grepped initially turned out to *not* use `java.time` — the false positive came from importing the `:common.datetime.*` interface, not from `java.time` itself. Tightened blast radius from "16 files" to "7 actually-modified files".

## Build verification

| Task | Result |
|---|---|
| `:common:assembleDebug` | ✅ |
| `:common:test` | ✅ |
| `:common:compileKotlinIosSimulatorArm64` | ✅ |
| `:common:compileKotlinIosArm64` | ✅ |
| `:common:compileKotlinIosX64` | ✅ |
| `:app:assembleDebug` | ✅ 12s |
| `./gradlew test` (whole project) | ✅ |

## Deviations from the plan

The plan's Phase 2 had two halves: (A) datetime swap, (B) `:domain` becomes a KMP module + Room KMP. **Only (A) shipped.** (B) is folded into Phase 5 — see PLAN.md update for rationale.

The decision: `:domain`-shell-only (no content move) doesn't unlock anything Phases 3 or 4 need. Phase 3 touches `:remote:*`. Phase 4 swaps Hilt to Koin (touches `:domain` regardless of its KMP shape). Phase 5 has to migrate `:domain` source to commonMain *anyway* once Paging Multiplatform is wired (to unblock Room source-in-commonMain). Doing a `:domain` build-shell move in Phase 2 is busywork that gets re-done in Phase 5.

## Lessons (candidates for `.claude/lessons.md` retrospective)

- **`@Serializer(forClass = X::class)` requires X to be in the same module.** kotlinx-datetime types aren't, so the cross-module annotation form fails to generate. Workaround: explicit `descriptor: SerialDescriptor` declaration on the `KSerializer` implementation. Costs three lines of code, no functional change.
- **kotlinx-datetime has no `LocalDateTime.MIN` / `LocalDateTime.MAX`.** Tests using these as sentinels need concrete replacements. `LocalDateTime(1970, 1, 1, 0, 0)` and `LocalDateTime(9999, 12, 31, 23, 59, 59)` work fine for sort-order assertions.
- **kotlinx-datetime's format builder can't do locale-aware month abbreviations** (`MMM` produces "Jan", "Feb", … always in English regardless of locale). Anything that needs localized month names still needs platform `expect/actual` delegating to `java.time.DateTimeFormatter` or `NSDateFormatter`.
- **False-positive scope**: my initial grep for `java.time|LocalDateTime|...` matched files importing the `:common.datetime.*` *interface*, not the JVM types. Phase 2's actual blast radius was less than half of what I initially reported. Lesson: in future migration phases, grep for the JVM-only `java.time.*` import line specifically, not the type names.

## Next phase

Phase 3 — `:remote:*` networking swap. Retrofit + Sandwich → Ktor + sandwich-ktor. Per the plan, both `:remote:cheapshark` and `:remote:gamerpower` get rewritten. Engine becomes `expect`-provided per platform (OkHttp on Android, Darwin on iOS). `MockWebServer` swapped for Ktor `MockEngine` in tests.
