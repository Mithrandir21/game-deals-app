# Phase A5 — Feature ViewModel tests to commonTest (35 net iOS tests)

**Branch:** `feature/kmp-migration-phase-A5-feature-viewmodel-tests`
**Started:** 2026-05-05
**Status:** Build-green Android + iOS; 77 tests now run on `iosSimulatorArm64Test` (was 42 after A4).

## Why this phase

After A4 lifted the simpler `:domain` repository tests, the remaining iOS
coverage gap was the 6 feature ViewModel tests. They're orchestration-heavy
(VM → Repository), so they need a working KMP mocking story. They also use
`MainCoroutineRule` — a JUnit `TestWatcher` — which doesn't port to KMP as-is.

This phase split into three sub-commits:
- **A5a** — testing utils moved to `:testing/commonMain` (`observeEmissions`,
  `nth()` accessors).
- **A5b** — Repository interface extraction (the big architectural change
  this phase forced).
- **A5c** — 6 feature VM tests lifted to commonTest.

## A5a — `observeEmissions` and friends to commonMain

`testing/src/.../utils/TestingExtensions.kt` (Flow observation helper +
`second()..tenth()` list accessors) had only one Android-bound import — an
unused KDoc reference to `MainCoroutineRule`. Body was already pure KMP.

Moved to `commonMain`. Dropped the unused `MainCoroutineRule`,
`TestScope`, `UnconfinedTestDispatcher` imports (KDoc-only references).
Rewrote the KDoc to drop the `MainCoroutineRule` mention. Moved
`libs.coroutines.testing` from `androidMain.dependencies` to
`commonMain.dependencies` so `TestDispatcher` resolves on iOS.

## A5b — Repository interface extraction (the architectural step)

**The blocker.** Mokkery 2.10.2 cannot mock final classes. All 5 repositories
in `:domain` were concrete final classes (`class XxxRepository internal
constructor(...)`), so any test that wanted to mock a Repository at the VM
boundary couldn't port. Three options:

1. Apply the all-open compiler plugin to make Repositories open. Adds a
   plugin, makes prod code's classes open at runtime (no inlining,
   virtual calls), feels like a hack to satisfy a test framework.
2. Use real Repositories with mocked DAO/Source. Test scope expands from
   "VM unit test" to "VM+Repo integration"; constructors are `internal`
   so they'd need to become `public` first, and most VMs touch enough
   collaborators that the indirection becomes noisy.
3. **Extract Repository interfaces, rename concrete classes to `*Impl`.**
   Architectural improvement that's correct independent of testing —
   interfaces enable swappable impls, cleaner DI, decoupling. Unblocks
   all 6 VM tests with one mechanical refactor.

Picked option 3.

Each of the 5 repositories (`Deals`, `Games`, `Giveaways`, `Releases`,
`Stores`) gets:

```kotlin
interface XxxRepository {
    // public method signatures
}

internal class XxxRepositoryImpl(...) : XxxRepository {
    override fun ...
}
```

The interface keeps the original name so callers (ViewModels, DI module)
don't break. The concrete becomes `internal class *Impl`. DI module:
`single<XxxRepository> { XxxRepositoryImpl(get(), ...) }` — Koin resolves
the interface to the impl.

Existing repository tests in `:domain/commonTest` (lifted in A4) updated
to instantiate `XxxRepositoryImpl(...)` for the SUT — they test the impl
through the interface contract, which is exactly the right shape.
`DealsRepositoryTest` (still on `androidUnitTest` + MockK) gets the same
rename.

## A5c — 6 feature ViewModel tests to commonTest

| Test | Module | Tests | Notes |
|---|---|---|---|
| `SearchViewModelTest`  | `:feature:search`    |  6 | smallest, used as the VM-port POC |
| `StoreViewModelTest`   | `:feature:store`     |  3 | uses `SavedStateHandle` (works in commonMain via lifecycle-viewmodel-multiplatform) |
| `GameViewModelTest`    | `:feature:game`      |  4 | mocks 2 repos + uses real `GameDetails` via `gameDetails()`/`gameDeal()` factories |
| `GiveawaysViewModelTest` | `:feature:giveaways` | 10 | uses `everySuspend { … } calls { … }` for suspend dynamic answers |
| `DealDetailsViewModelTest` | `:feature:deal`  |  4 | most data-class construction (`DealDetails` + nested `GameInfo`/`CheaperStore`/`CheapestPrice`) |
| `HomeViewModelTest`    | `:feature:home`      |  8 | most complex; one test uses `var activeFetches = 0` instead of `AtomicInteger` |

### Conversion shape (mechanical across all 6 files)

| MockK | Mokkery |
|---|---|
| `mockk<Repository>()` | `mock<Repository>(MockMode.autoUnit)` |
| `coEvery { suspendFn() } returns x` | `everySuspend { … } returns x` |
| `coEvery { plainFn() } returns x` | `every { … } returns x` |
| `coEvery { … } just runs` | drop entirely; `autoUnit` covers Unit-returning fns |
| `coVerify(exactly = N) { … }` | `verifySuspend(exactly(N)) { … }` |
| `verify(exactly = N) { … }` | `verify(exactly(N)) { … }` |
| `coAnswers { suspend body }` | `everySuspend { … } calls { suspend body }` |

| Other | |
|---|---|
| `@get:Rule MainCoroutineRule()` | `@BeforeTest Dispatchers.setMain(testDispatcher)` + `@AfterTest Dispatchers.resetMain()` (inline) |
| `mockk<DataClass>()` value object | constructed via per-file factory helpers (`deal()`, `store()`, `giveaway()`, `gameDetails()`, etc.) |
| `mockk<X> { every { property } returns y }` | `x(property = y)` factory call |
| `org.junit.{Test, Before, Assert.*}` | `kotlin.test.{Test, BeforeTest, AfterTest, assertEquals, assertNotNull, assertNull}` |
| Backtick test names | snake_case |
| `java.util.concurrent.atomic.AtomicInteger` (single-thread test counter) | `var counter = 0` (TestScope is single-thread) |

### Per-module build wiring

Every `feature/*/build.gradle.kts` got:
```kotlin
plugins {
    // existing plugins...
    alias(libs.plugins.mokkery)
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing"))
            implementation(libs.coroutines.testing)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                // libs.core.testing where it was previously needed
            }
        }
    }
}
```

`libs.coroutines.testing` and `:testing` move from `androidUnitTest` to
`commonTest` so iOS test source sets resolve them. `mockk` + `junit` stay
in `androidUnitTest` because the `androidInstrumentedTest` Compose UI tests
in each feature still depend on them via the implicit androidTest
hierarchy.

## Build verification

| Task | Result |
|---|---|
| `:feature:search:iosSimulatorArm64Test` | ✅ 6 tests |
| `:feature:store:iosSimulatorArm64Test` | ✅ 3 tests |
| `:feature:game:iosSimulatorArm64Test` | ✅ 4 tests |
| `:feature:giveaways:iosSimulatorArm64Test` | ✅ 10 tests |
| `:feature:deal:iosSimulatorArm64Test` | ✅ 4 tests |
| `:feature:home:iosSimulatorArm64Test` | ✅ 8 tests |
| All 6 `:feature:*:testDebugUnitTest` | ✅ Android side identical |
| `./gradlew test :app:assembleDebug` | ✅ 456 tasks |

**Total iOS test count: 77** (was 42 after A4 → +35 in this phase).

| Module | iOS tests after A4 | iOS tests after A5 | Δ |
|---|---|---|---|
| `:common` | 11 | 11 | – |
| `:domain` | 21 | 21 | – |
| `:remote:cheapshark` | 9 | 9 | – |
| `:remote:gamerpower` | 1 | 1 | – |
| `:feature:search` | 0 | 6 | +6 |
| `:feature:store` | 0 | 3 | +3 |
| `:feature:game` | 0 | 4 | +4 |
| `:feature:giveaways` | 0 | 10 | +10 |
| `:feature:deal` | 0 | 4 | +4 |
| `:feature:home` | 0 | 8 | +8 |
| **Total** | 42 | **77** | **+35** |

## Lessons (candidates for `.claude/lessons.md`)

- **Mokkery requires interfaces (or open classes) for collaborators.** A
  codebase that uses concrete final Repository classes can't port VM
  tests until interfaces are extracted. Cheaper to do interface
  extraction once and unblock all VM tests than to try alternative
  approaches (real Repos, all-open plugin, etc.).
- **Repository interface extraction has a benefit beyond testing.** The
  `interface Repo + internal class RepoImpl` shape is what an experienced
  Android codebase typically lands on anyway. Tests are the pressure that
  forces it; the win is real architectural decoupling.
- **`MainCoroutineRule` doesn't need a KMP equivalent.** The JUnit
  `TestWatcher` shape is replaced by `@BeforeTest Dispatchers.setMain(...)`
  + `@AfterTest Dispatchers.resetMain()` inline in each test class. Three
  lines of boilerplate per file, no shared infra needed. Don't extract a
  KMP "MainCoroutineRule equivalent" — it adds an abstraction that hides
  what's actually a tiny pattern.
- **`every` vs `everySuspend` is strict, BUT applies to non-suspend
  collaborator method on a suspend interface too.** `Repository.observeXxx():
  Flow<...>` is non-suspend even though the interface might also have
  suspend methods. Always check the specific method's modifier.
- **`mockk<DataClass>()` is a code smell that surface-level migration
  eliminates.** Every place we replaced it with `xxx(field = value)` factory
  calls, the test became more readable and type-safe. The mock-each-property
  pattern was hiding awkward construction; the migration replaces the awkward
  with explicit named-arg construction.
- **`SavedStateHandle` works in commonMain.** Via `androidx.lifecycle:
  lifecycle-viewmodel-multiplatform`, `SavedStateHandle` is KMP-compatible
  and available on iOS. Tests can construct it with `SavedStateHandle(mapOf(
  "key" to value))` exactly the same on both platforms.
- **For single-thread `runTest` counters, `var` beats `AtomicInteger`.**
  TestScope's dispatcher is single-thread by default, so `var counter = 0`
  works correctly without atomicity. KMP-portable as a bonus.

## What's still on `androidUnitTest` (deliberate)

- **`DealsRepositoryTest`** — uses `mockkStatic("androidx.room.RoomDatabaseKt")`
  + `slot/capture`. Mokkery doesn't support either; rewrite with real
  Room is better than a port. Stays androidUnitTest indefinitely.
- **`SettingStorageTest`** (`:common`) — uses Android `SharedPreferences`.
  Storage abstraction is Android-only by design; not relevant for iOS.
- **`DateTimeFormatterImplTest`** (`:common`) — Android-side `SimpleDateFormat`
  variant. The iOS-side `NSDateFormatter` impl has its own coverage path.

## What's NOT covered on iOS

- **Compose UI tests (`androidInstrumentedTest`)** — 8 tests per feature
  module. iOS Compose snapshot testing is a separate framework decision
  per PLAN.md decision K. Deferred indefinitely.
- **`DealsMediatorTest`** — Paging-3 tests. Paging-3 was dropped in A1 (γ),
  so this test was already gone.
