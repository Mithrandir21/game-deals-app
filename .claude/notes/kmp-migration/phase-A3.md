# Phase A3 — iOS test bring-up: lift 5 tests to commonTest, adopt Mokkery

**Branch:** `feature/kmp-migration-phase-A3-ios-tests`
**Started:** 2026-05-05
**Status:** Build-green Android + iOS; 21 new tests now run on `iosSimulatorArm64Test`.

## Why this phase

PLAN.md decision **K** deferred iOS unit tests until end of session. Phase 7.8
already mirrored two pure-pure-Kotlin classes (`DatetimeParsingImplTest`,
`CurrencyTransformationImplTest`) without committing to a mocking strategy.
This phase makes the broader call: how do iOS tests get a chance against the
existing 22 MockK-using files when MockK is JVM-only by design.

Decision (after grilling on the alternatives):

- **Adopt Mokkery 3.3.0** as the KMP-friendly mocking choice for *new*
  commonTest files. Mokkery's API is ~95% MockK-compatible (`mockk` → `mock`,
  `coEvery` → `everySuspend`, `verify` → `verify`, `coVerify` → `verifySuspend`).
  Compiler-plugin driven, so it works on Kotlin/Native unlike MockK's runtime
  bytecode rewriting.
- **Don't sweep** the existing 22 MockK files. Lift them lazily, when they're
  refactored or when iOS coverage of a specific subsystem is needed.
- **Hand-rolled fakes** for collaborators with 1–2 methods used in a single
  test. Cheaper than the library swap for that scope.
- **Keep `DealsRepositoryTest` on MockK** indefinitely — it uses
  `mockkStatic("androidx.room.RoomDatabaseKt")` (not portable to Mokkery) plus
  `slot { } + capture()` (no direct equivalent). Room transaction behavior is
  better tested via in-memory Room round-trips on Android than via lambda
  capture mocks.

## A3a — lift the 3 zero-mockk tests + register Mokkery

Mirrors three pure-Kotlin unit tests that don't use *any* mocking library —
they were waiting on a mocking decision because they sit alongside MockK-using
tests in the same module, but their own bodies are fake-free.

| Test | Module | Tests | What it covers |
|---|---|---|---|
| `FlowExtensionsTest` | `:common` | 8 | virtual-time `*DelayAtLeast` Flow operators |
| `GiveawaySearchParametersTest` | `:domain` | 2 | `Properties.encodeToMap`/`from` round-trip |
| `CachedResourceTest` | `:domain` | 7 | TTL freshness boundary with hand-rolled `MutableClock` |

Mechanical changes per file:
- `org.junit.{Test,Before,Assert.*}` → `kotlin.test.{Test,BeforeTest,assertEquals,assertTrue,assertFalse}`
- Backtick test names → snake_case (Kotlin/Native rejects backticks; matches phase-7.8 convention)
- `kotlinx.coroutines.flow.flow {}` import added to `FlowExtensionsTest` (had been fully-qualified)

Build wiring:
- `:common/commonTest` gains `libs.coroutines.testing` (`runTest` + `testScheduler`).
- `:domain` gains a fresh `commonTest.dependencies { kotlin("test") + coroutines.testing }` block.
- `gradle/libs.versions.toml` — `mokkery = "3.3.0"` registered as plugin alias `dev.mokkery`. **Plugin not yet applied to any module** — recording the decision without dragging it into this slice.

## A3b — `:testing` to KMP + lift two source-impl tests with inline fakes

`:testing` was the last Android-only library module not yet on the KMP
convention. Migrating it unblocks any commonTest that needs `TestingLoggingListener`.

### `:testing` migration
- Convention swap `gamedeals.android.library` → `gamedeals.kmp.library`.
- `TestingLoggingListener.kt` → `commonMain/kotlin/`. One portability fix:
  `this.javaClass.simpleName` → `this::class.simpleName` (`javaClass` is JVM-only).
- `MainCoroutineRule.kt` → `androidMain/kotlin/` (JUnit `TestWatcher`-based; stays Android-only until a KMP `runTest`-based replacement is wired).
- `TestingExtensions.kt` (which holds `observeEmissions` + the `nth()` helpers) → `androidMain/kotlin/` for now; only feature ViewModel tests (still Android-only) consume it. Could be lifted later.
- `BuildConfig` was enabled but never referenced — dropped.
- Test deps (`mockk`, `junit`, `coroutines.testing`, `androidx.runner`) move from `dependencies { }` to `androidMain.dependencies { }`.

### Source-impl test lifts
| Test | Module | Tests | Inline fake(s) |
|---|---|---|---|
| `CheapsharkSourceImplTest` | `:remote:cheapshark` | 5 | `FakeCurrencyTransformation` (1 method), `FakeDateTimeFormatter` (2 methods) |
| `GamerPowerSourceImplTest` | `:remote:gamerpower` | 1 | `FakeDatetimeParsing` (1 stubbed method, 2 `error("not stubbed")`) |

Inline private classes at the bottom of each test file. The fakes are tiny —
each interface has 1–2 methods relevant to the test, and a `mockk { every { ... } returns x }`
becomes a 3-line Kotlin class. Cheaper than committing the test to Mokkery's
compiler-plugin codegen for one shallow stub.

`:remote:*` build wiring: `:testing` + `coroutines.testing` + `ktor.client.mock`
move from `androidUnitTest` to `commonTest` so iOS test source sets resolve
them. `mockk` + `junit` stay in `androidUnitTest` (still consumed by other
Android-only tests in those modules).

## Build verification

| Task | Result |
|---|---|
| `:common:iosSimulatorArm64Test` | ✅ 11 tests (8 new + 3 existing) |
| `:domain:iosSimulatorArm64Test` | ✅ 9 tests (7 + 2 new) |
| `:remote:cheapshark:iosSimulatorArm64Test` | ✅ 9 tests (5 new + 4 existing) |
| `:remote:gamerpower:iosSimulatorArm64Test` | ✅ 1 test (new) |
| `:testing:compileKotlinIosSimulatorArm64` + `compileDebugKotlinAndroid` | ✅ |
| `:common:testDebugUnitTest`, `:domain:testDebugUnitTest`, `:remote:*:testDebugUnitTest` | ✅ Android side unchanged |
| `./gradlew test :app:assembleDebug` (whole project) | ✅ 456 tasks green |

**Total iOS test count: 30** (up from 7 at the start of A3 — the two phase-7.8
mirrored classes plus the 23 lifted in A3a + A3b... actually 11+9+9+1=30, of
which 7 were already on iOS via phase-7.8, so net +23 tests on iOS; 21 distinct
new test methods if we count CurrencyTransformationImplTest from phase-7.8 as
already-on-iOS).

## Deviations from plan

- **No new fakes in `:testing/commonMain`.** Earlier sketches put `FakeDateTimeFormatter`
  + `FakeCurrencyTransformation` + `FakeDatetimeParsing` as shared fixtures in
  `:testing`. Backing out: `CurrencyTransformation` lives in `:remote:cheapshark`;
  if `:testing/commonMain` provided a fake of it, `:testing` would need to depend
  on `:remote:cheapshark`, which already depends on `:testing` for tests — circular.
  Inline private fakes per test file avoid the circularity and keep each fake
  scoped to its consumer. If a third test ever needs one of these, lift it then.
- **Mokkery registered but not applied.** Phase A3 didn't actually use Mokkery
  yet — every lifted test had a fake-friendly shape. Plugin alias is in the
  catalog so the next phase that needs `verify { }` semantics on commonTest can
  apply `id(libs.plugins.mokkery)` to its module without a separate catalog change.

## Lessons (candidates for `.claude/lessons.md`)

- **MockK is JVM-only by design.** `io.mockk:mockk` uses Byte Buddy / dexmaker;
  there's no Kotlin/Native path. Issue mockk/mockk#58 has been open since 2018
  with no movement. Don't add mockk to `commonTest.dependencies` — use Mokkery
  (compiler-plugin) or hand-rolled fakes.
- **Mokkery API mapping cheat-sheet.** `mockk` → `mock`, `coEvery` → `everySuspend`,
  `coVerify` → `verifySuspend`, `relaxed = true` → `mock<T>(MockMode.autofill)`,
  `relaxUnitFun = true` → `mock<T>(MockMode.autoUnit)`, `every { ... } answers
  { firstArg<T>() }` → `every { ... } calls { (a: T) -> a }`. **Doesn't support:**
  top-level/extension functions, sealed types, `object`s, `slot/capture` (use
  `calls { (arg) -> ... }` for arg-driven behavior, no direct capture-then-assert).
- **Inline private fakes beat library swaps for shallow stubs.** When a test
  has 1–3 single-method interface mocks, a `private class FakeFoo : Foo { override
  fun bar() = "x" }` at the bottom of the test file is cheaper than wiring a new
  mocking framework. Reach for the framework when a test verifies call sequences,
  uses `verify { }` semantics, or needs argument-driven dynamic answers.
- **`this.javaClass` is JVM-only.** When lifting a class to commonMain, swap to
  `this::class` for `simpleName`/`qualifiedName`. The Kotlin reflection API
  exposes a small but portable subset; `javaClass` is the JVM bridge property.
- **`commonTest` classes are visible to `androidUnitTest`** via the KMP source-set
  hierarchy — no need to also place a copy under androidUnitTest. The opposite
  is **not** true: classes in `androidUnitTest` are not visible to `commonTest`.
- **`UnitTest` source sets compile against `commonTest`** but Gradle still produces
  per-target test reports under `build/test-results/{testDebugUnitTest,iosSimulatorArm64Test,...}/`.
  Quickest sanity check that an iOS test ran: `find build/test-results/iosSimulatorArm64Test -name 'TEST-*.xml'`.
