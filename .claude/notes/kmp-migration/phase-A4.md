# Phase A4 — Mokkery proof-of-concept + 4 `:domain` repository tests to commonTest

**Branch:** `feature/kmp-migration-phase-A4-domain-tests`
**Started:** 2026-05-05
**Status:** Build-green Android + iOS; 12 new tests on iOS; total iOS tests 30 → 42.

## Why this phase

Phase A3 registered Mokkery in the catalog but didn't apply it anywhere — every
test lifted in A3 was either zero-mockk or single-collaborator-fake friendly.
A4 is the first phase where Mokkery actually does work. The `:domain` module's
five repository tests are heavy on `coEvery`/`coVerify` orchestration, so
they're the canonical "do we have a working KMP mocking story" test.

Decision (carried over from A3):
- Mokkery is the KMP-friendly mocking choice. ~95% MockK-shaped API but driven
  by a compiler plugin so it works on Kotlin/Native.
- Existing 22 MockK tests stay on `androidUnitTest` until they're refactored.
  A4 lifts the 4 of 5 `:domain` repository tests that fit; the 5th
  (`DealsRepositoryTest`) stays Android-only because it uses `mockkStatic` +
  `slot/capture` which Mokkery doesn't support.

## Mokkery version pinning

First pass tried Mokkery **3.3.0** (latest as of 2026-03). Plugin refused to
apply with:

```
Current Kotlin version must be at least 2.3.0, but is 2.2.21!
```

Mokkery's compatibility rules: 2.x → Kotlin 2.0/2.1/2.2; 3.x → Kotlin 2.3+.
We're on Kotlin 2.2.21 (locked in phase-0). Pinned to **Mokkery 2.10.2**
(Oct 2025) — last 2.x release; explicitly bumps supported Kotlin to 2.2.21.

Recorded in `gradle/libs.versions.toml` as `mokkery = "2.10.2"` against the
existing `dev.mokkery` plugin alias.

## A4a — proof of concept: `ReleasesRepositoryTest`

Smallest mockk-using repo test (2 tests, 20 mockk usages, no slot/static).
Lifted as the canonical Mokkery shape demonstrator before sweeping the others.

Mechanical conversions (apply to all subsequent A4b tests too):

| MockK | Mokkery |
|---|---|
| `mockk<T>()` collaborator | `mock<T>(MockMode.autoUnit)` |
| `mockk<DataClass>()` value | constructed real instance via factory helper |
| `coEvery { suspendFn() } returns x` | `everySuspend { suspendFn() } returns x` |
| `coEvery { plainFn() } returns x` | `every { plainFn() } returns x` (Mokkery is strict; non-suspend gets `every`) |
| `coEvery { … } just runs` | drop entirely; autoUnit handles Unit returns |
| `coVerify(exactly = N) { … }` | `verifySuspend(exactly(N)) { … }` |
| `verify(exactly = N) { … }` | `verify(exactly(N)) { … }` |
| `coVerify(exactly = 0) { fn(*anyVararg()) }` | `verifySuspend(exactly(0)) { fn(*anyVararg<T>()) }` |

Imports the test ends up needing:
```kotlin
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.varargs.anyVarargs
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
```

Also dropped: `@get:Rule InstantTaskExecutorRule()` from every test — it
existed for LiveData/Arch-Components, but these tests use `Flow + runTest`
exclusively. Pure dead weight that wouldn't port to KMP anyway (the rule is
JUnit + Android-only).

## A4b — sweep `Stores`, `Games`, `Giveaways` repository tests

Three more tests applied the same conversion shape from A4a.

### `StoresRepositoryTest` (4 tests)
Most interesting conversion: previously `every { fetched.copy(expires = X) }
returns stamped` mocked the data class's `copy()` method. Mokkery cannot mock
final classes, so the mock-the-copy pattern is replaced by calling the real
`Store.copy(expires = X)` inside the verify block:

```kotlin
verifySuspend(exactly(1)) {
    storesDao.addStores(fetched.copy(expires = expectedExpires))
}
```

Data classes have value equality, so the verify still pins the impl's
stamping behavior. The synthetic `verify { fetched.copy(...) }` intent
check from the original is dropped (it was implementation detail).

### `GamesRepositoryTest` (3 tests)
Trivial conversion. `mockk<Game>()` + `mockk<GameDetails>()` were opaque
pass-through values (no method calls on them). Replaced with constructed
real instances via private `game()` + `gameDetails()` factories.

### `GiveawaysRepositoryTest` (3 tests)
Most lines changed but conceptually simplest. The original used the
mock-each-property pattern repeatedly:

```kotlin
val resultOne = mockk<Giveaway> {
    every { type } returns GiveawayType.GAME
    every { platforms } returns listOf(GiveawayPlatform.PC)
    every { publishedDate } returns MIN_DATETIME
    every { users } returns 1
    every { worth } returns 1.0
}
```

Replaced with a single named-arg construction:
```kotlin
val resultOne = giveaway(
    type = GiveawayType.GAME,
    platforms = listOf(GiveawayPlatform.PC),
    publishedDate = MIN_DATETIME,
    users = 1,
    worth = 1.0,
)
```

`Giveaway` has 17 required fields; `private fun giveaway(…)` at the bottom
of the file supplies sensible defaults so each test only spells out the
fields that matter to it. Reads better than the mock approach.

## What stays Android-only

`DealsRepositoryTest` — 5 tests, 48 mockk usages, 4 `slot<…>` calls, 3
`mockkStatic` calls. Two Mokkery limitations hit it simultaneously:
1. `mockkStatic("androidx.room.RoomDatabaseKt")` mocks the Room
   `withTransaction` extension function. Mokkery can't mock top-level /
   extension functions.
2. `slot<suspend (Transactor) -> Unit>()` + `capture(slot)` captures Room's
   transaction lambda for verification. Mokkery has `calls { (arg) -> … }`
   for arg-driven *answers* but no direct `slot/capture` for *assert-on-
   captured-value-later*.

Either limitation alone would force a rewrite; together they make this
test the wrong vehicle for a Mokkery port. Room transaction behavior is
better tested via in-memory Room round-trips on Android than via lambda
capture mocks anyway. Leaving on `androidUnitTest` indefinitely.

## Build verification

| Task | Result |
|---|---|
| `:domain:iosSimulatorArm64Test` | ✅ 21 tests across 6 classes (12 net new on iOS this phase) |
| `:domain:testDebugUnitTest` | ✅ 26 tests (same 6 + DealsRepositoryTest's 5 Android-only) |
| `./gradlew test :app:assembleDebug` | ✅ 456 tasks |

**Total iOS test count: 42** (was 30 after A3).

| Module | iOS tests after A3 | iOS tests after A4 | Δ |
|---|---|---|---|
| `:common` | 11 | 11 | – |
| `:domain` | 9 | **21** | +12 |
| `:remote:cheapshark` | 9 | 9 | – |
| `:remote:gamerpower` | 1 | 1 | – |
| **Total** | 30 | **42** | **+12** |

## Lessons (candidates for `.claude/lessons.md`)

- **Mokkery's Kotlin version pairing is hard.** 2.x for Kotlin 2.0–2.2.x,
  3.x for Kotlin 2.3+. Pin to **Mokkery 2.10.2** while the project is on
  Kotlin 2.2.21; bumping requires a Kotlin upgrade first. The 2.10.x line
  will see no further updates — it's the EOL branch for Kotlin 2.2.x.
- **`every` vs `everySuspend` (and `verify` vs `verifySuspend`) is strict
  in Mokkery.** Unlike MockK where `coEvery` accepts non-suspend fns, in
  Mokkery you'll hit "Cannot infer suspending context" or "Function is
  not suspending" errors. Match the modifier to the function's actual
  shape.
- **`anyVarargs<T>()` needs spread + explicit type.** Mokkery can't infer
  `T` from a `vararg T` parameter when used as a matcher. Always write
  `fn(*anyVarargs<Type>())`, never `fn(anyVarargs())`.
- **Mock-each-property on data classes is a code smell that Mokkery
  forces you to fix.** The original `mockk<Giveaway> { every { type }
  returns ...; every { platforms } returns ... }` blocks become
  `giveaway(type = …, platforms = …)` calls. The test reads better and
  the construction is type-safe at compile time.
- **Mock-the-copy pattern (`every { obj.copy(...) } returns stamped`) is
  not portable.** Replace with `obj.copy(...)` inside the verify block —
  data class value equality picks up the actual call. You lose the
  intent-check that "the impl called copy()" but gain the stronger check
  that "the impl produced a value with the right field set".
- **`InstantTaskExecutorRule` is dead in any test that uses Flow +
  runTest.** When porting Android-only tests to commonTest, drop the
  rule — it doesn't port and it wasn't doing anything for Flow-based
  code in the first place.
- **`mockkStatic` + `slot/capture` are the two Mokkery non-starters.**
  When triaging a test's portability, grep for these two patterns first.
  Any test with `mockkStatic("…")` or `slot<…>() + capture(…)` will need
  a rewrite, not a port — and likely the rewrite is "use real values
  + in-memory infrastructure", not "find a different mocking library".
