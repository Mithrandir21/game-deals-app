---
**Path scope:** `testing/**`, `*/src/commonTest/**`, `*/src/androidHostTest/**`, `*/src/androidDeviceTest/**`, `*/src/androidTest/**`
**Last surveyed:** 34b01013 on 2026-05-18
---

# Testing

This codebase keeps a deliberate split between commonTest (KMP-portable JVM + Native unit tests), androidHostTest (JVM-only Android unit tests), and androidDeviceTest / androidTest (instrumented). The `:testing` module ships shared infrastructure: a `MainDispatcherTest` abstract base class that installs a `TestDispatcher` as `Dispatchers.Main`, an `observeEmissions()` Flow collector, a `MockHttpClient` factory backed by Ktor's `MockEngine`, and a `fixtures/` builder DSL. Mocking is Mokkery 3.3.0 in commonTest (MockK survives only in androidHostTest). Instrumented tests use Koin test override modules loaded by `TestGameDealsApplication`, orchestrated via `KoinTestRunner`. Naming uses backtick style.

The **Compose UI test finder policy** (how to locate nodes in instrumented `*ScreenTest.kt` files, why `testTag` is forbidden, when to add `Modifier.semantics`) is documented separately in [ui-testing.md](ui-testing.md).

## Patterns

### `MainDispatcherTest` abstract base class

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every commonTest / androidHostTest class touching coroutines or Flows

**The pattern.**
Tests extend `MainDispatcherTest`, an abstract base in `:testing/commonMain`. The base exposes `protected val testDispatcher: TestDispatcher` plus `installMainDispatcher()` / `resetMainDispatcher()` helpers that the subclass wires into `@BeforeTest` and `@AfterTest`. This replaces the prior JUnit 4 `MainCoroutineRule`, which couldn't run on Kotlin/Native. The shared `observeEmissions()` extension still launches a collection job on the test dispatcher and returns a mutable list filled asynchronously — tests call it with `this.backgroundScope` (from `runTest`) to observe state/events without blocking or missing emissions.

**Why this works for us.**
KMP-portable seam: `kotlin.test` annotations run on both JVM and Native; JUnit 4 rules do not. One place to change the dispatcher policy. Aligns dispatcher control with `runTest` virtual-time semantics; `UnconfinedTestDispatcher` greedy-executes jobs so observer and subject run synchronously inside `runTest`, eliminating flakiness. `backgroundScope` cancels automatically at test end.

**Known trade-offs / when it strains.**
Requires per-class `@BeforeTest`/`@AfterTest` boilerplate (vs. JUnit 4's auto rule) — the trade is necessary for commonTest portability. The mutable list collects every historical emission, so tests assert on indices (`first()`, `last()`, `size`); rapidly emitting flows make order-sensitive assertions fragile.

**How to apply it.**
```kotlin
class MyViewModelTest : MainDispatcherTest() {

  @BeforeTest fun setUp() = installMainDispatcher()
  @AfterTest fun tearDown() = resetMainDispatcher()

  @Test
  fun test_example() = runTest {
    val vm = MyViewModel(fakeRepo)
    val states = vm.uiState.observeEmissions(this.backgroundScope, testDispatcher)

    assertEquals(1, states.size)
    assertEquals(expectedState, states.first())
  }
}
```

**Seen in.**
- testing/src/commonMain/kotlin/pm/bam/gamedeals/testing/MainDispatcherTest.kt
- testing/src/commonMain/kotlin/pm/bam/gamedeals/testing/utils/TestingExtensions.kt
- feature/home/src/commonTest/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModelTest.kt
- feature/game/src/commonTest/kotlin/pm/bam/gamedeals/feature/game/ui/GameViewModelTest.kt

**Related lessons.** L-2026-05-01-07, L-2026-05-02-05

### Mokkery 3.3.0 in commonTest

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all commonTest mocks; androidHostTest where Native portability isn't required

**The pattern.**
Tests in `commonTest` (and androidHostTest) use Mokkery 3.3.0 for mocking. DSL: `mock<MyInterface>(MockMode.autoUnit) { … }` for construction, `every { … } returns …` for sync stubs, `everySuspend { … } returns …` for suspend stubs, `verify { … }` / `verifySuspend { … }` for verification. The KSP processor generates platform mocks for both JVM and Native targets. Mokkery 3.x uses spread-vararg matching (`*anyVararg()`) instead of the older `anyVarargs()` shape.

**Why this works for us.**
Works in commonTest across JVM + Kotlin/Native — MockK is JVM-only, so a shared mocking library is the price of admission for KMP unit tests. Type-safe across the platform boundary. The DSL is close enough to MockK that the muscle-memory carries over.

**Known trade-offs / when it strains.**
Smaller ecosystem than MockK; some advanced features (extension-function mocking, deep stubs, partial mocks) differ in shape or aren't supported. KSP processor adds compile-time overhead.

**How to apply it.**
```kotlin
private val source: CheapsharkSource = mock()

@Test
fun `fetch deals delegates to source`() = runTest {
  everySuspend { source.fetchDeals(any()) } returns listOf(deal())

  val result = repo.fetchDeals(query)

  verifySuspend { source.fetchDeals(any()) }
  assertEquals(1, result.size)
}
```

**Seen in.**
- domain/src/commonTest/kotlin/pm/bam/gamedeals/domain/repositories/games/GamesRepositoryTest.kt
- feature/home/src/commonTest/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModelTest.kt
- remote/cheapshark/src/commonTest/kotlin/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImplTest.kt

**Related lessons.** L-2026-05-06-04, L-2026-05-11-03, L-2026-05-11-04, L-2026-05-15-04

### Virtual Time Discipline via `testScheduler.currentTime`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** delay-sensitive operator tests (`FlowExtensionsTest`)

**The pattern.**
Tests of delay-based operators (`mapDelayAtLeast`, `flatMapLatestDelayAtLeast`, `withMinimumDuration`) capture `testScheduler.currentTime` before and after the operation, then assert elapsed time. No wall-clock; entirely virtual. Tests verify that padding logic respects virtual time by using `delay(…)` inside the operator and measuring scheduler advance.

**Why this works for us.**
Virtual time is deterministic and fast — tests run instantly without real delays. If an operator regressed to `System.currentTimeMillis()`, the virtual scheduler would catch it because virtual time advances only via `delay()`.

**Known trade-offs / when it strains.**
Only applicable to operators that interact with time. Tests that compare elapsed time to a fixed constant are brittle if the constant shifts.

**How to apply it.**
```kotlin
@Test
fun `mapDelayAtLeast pads to delayMillis`() = runTest {
  val start = testScheduler.currentTime
  val result = flowOf(1)
    .mapDelayAtLeast(1000) { it * 2 }
    .toList()
  val elapsed = testScheduler.currentTime - start

  assertEquals(1000, elapsed)
  assertEquals(listOf(2), result)
}
```

**Seen in.**
- common/src/commonTest/kotlin/pm/bam/gamedeals/common/FlowExtensionsTest.kt

### Assertions + Mokkery Verify; No Assertion Framework

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all unit tests

**The pattern.**
commonTest uses `kotlin.test` (`assertEquals`, `assertTrue`, `assertFailsWith`, `assertNull`, `assertNotNull`); androidHostTest may keep `org.junit.Assert.*` where legacy tests linger. Behavior verification goes through Mokkery's `verify { … }` and `verifySuspend { … }`. No AssertJ, Hamcrest, or Kotest. Assertions stay scalar and scannable.

**Why this works for us.**
`kotlin.test` is part of the Kotlin standard distribution — no extra dependency, works on JVM and Native. Mokkery's `verify` integrates directly with the same mocks the test sets up. Most assertions are scalar (size, equality, boolean), so the trade-off is acceptable.

**Known trade-offs / when it strains.**
`kotlin.test` and JUnit assertions have terse error messages compared to AssertJ or Hamcrest. For complex multi-field comparisons, an assertion library would shine — we currently absorb that cost.

**How to apply it.**
```kotlin
assertEquals(expectedValue, actualValue)
assertTrue(condition)
assertFailsWith<ExceptionType> { block() }
verify { repo.fetch() }
verifySuspend { source.fetchDeals(any()) }
```

**Seen in.**
- feature/home/src/commonTest/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModelTest.kt
- domain/src/commonTest/kotlin/pm/bam/gamedeals/domain/repositories/games/GamesRepositoryTest.kt

### Room + `InstantTaskExecutorRule` for Synchronous DAO Tests

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** androidHostTest repository tests that exercise DAOs

**The pattern.**
Tests interacting with Room DAOs in androidHostTest declare `@get:Rule val instantExecutorRule = InstantTaskExecutorRule()` to force DAO operations onto the test thread, bypassing Architecture Components' default thread-hopping. The DAO and Database are mocked (Mokkery on JVM), and the test verifies the repository constructs queries, stamps data (e.g., expiry), and calls the DAO in the right order. Real Room usage is confined to instrumented tests that stand up an in-memory database.

**Why this works for us.**
Synchronous execution makes androidHostTest deterministic and fast. `InstantTaskExecutorRule` is lightweight. Mocking the DAO keeps the test focused on repository logic (retry, cache expiry, transactionality) without depending on schema or Room's async machinery.

**Known trade-offs / when it strains.**
DAO mocks don't catch schema mismatches or query bugs; those surface only in instrumented tests. For repositories that interact with multiple DAOs or rely on Room's query scheduling, mocking can obscure bugs. This rule is JUnit-4-only, so it lives in androidHostTest (not commonTest).

**How to apply it.**
```kotlin
@get:Rule
val instantExecutorRule = InstantTaskExecutorRule()

private val dao: MyDao = mock()

@Test
fun test_refresh_with_cache_expiry() = runTest {
  val expired = deal(expires = now - 10_000)
  everySuspend { dao.get(id) } returns listOf(expired)

  impl.refresh(id)

  verifySuspend { dao.clear(id) }
  verifySuspend { source.fetch(id) }
}
```

**Seen in.**
- domain/src/androidHostTest/kotlin/pm/bam/gamedeals/domain/repositories/deals/DealsRepositoryTest.kt

### `MockHttpClient` (Ktor MockEngine) for HTTP contract tests

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** remote source tests (e.g., `CheapsharkSourceImplTest`)

**The pattern.**
Tests that exercise HTTP clients call a `mockHttpClient(json) { request -> respond(…) }` factory that builds a Ktor `HttpClient` backed by `MockEngine`. The handler lambda inspects `request.url.path` (or method, headers, body) and returns a `respond(...)` with body + status + headers. No file fixtures; response bodies are constructed inline as string literals or via builders. The real Ktor pipeline (JSON serialization, error transformation) runs end-to-end against the engine.

**Why this works for us.**
KMP-portable: works in commonTest across JVM and Native. No Android `MockWebServer` dependency, no socket binding, no port allocation — tests stay hermetic and in-process. The handler shape is small enough to audit per test.

**Known trade-offs / when it strains.**
Harder to record real responses (no record/replay). Large response bodies live as string literals, which can drift from real server shapes. Mitigated by the per-feature fixtures DSL and by keeping the response constructions local to each test.

**How to apply it.**
```kotlin
@Test
fun `fetchStores decodes response`() = runTest {
  val client = mockHttpClient(json) { request ->
    assertEquals("/api/1.0/stores", request.url.encodedPath)
    respond("""[ { "storeID": "1", "storeName": "Steam" } ]""", HttpStatusCode.OK)
  }
  val impl = CheapsharkSourceImpl(client, …)

  val result = impl.fetchStores()

  assertEquals(1, result.size)
}
```

**Seen in.**
- testing/src/commonMain/kotlin/pm/bam/gamedeals/testing/MockHttpClient.kt
- remote/cheapshark/src/commonTest/kotlin/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImplTest.kt

**Related lessons.** L-2026-05-03-02, L-2026-05-03-03, L-2026-05-04-04

### Koin test module override pattern

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** instrumented tests in `:app` (and feature `androidDeviceTest`)

**The pattern.**
`TestGameDealsApplication` loads production Koin modules first, then test override modules (`testDatabaseOverridesModule`, `testNetworkOverridesModule`, `testImageLoaderOverridesModule`). Koin's last-load-wins semantics means the test bindings shadow production ones — an in-memory Room database, a `MockEngine`-backed `HttpClient`, and a fake `ImageLoader` fetcher replace the real bindings for the whole instrumented run. `KoinTestRunner` (extending `AndroidJUnitRunner`) instantiates `TestGameDealsApplication` so the override graph is in place before any test class loads.

**Why this works for us.**
No annotation-driven Hilt-style overrides to track. The layering is explicit, readable in one place, and works for any binding (HttpClient, Room, ImageLoader, Clock). The same approach extends to new collaborators without touching test infrastructure.

**Known trade-offs / when it strains.**
Globally-replaced bindings mean per-test customization requires either argument-driven flexibility on the override (e.g., a mutable response map) or `loadKoinModules`/`unloadKoinModules` reloads in setup — we deliberately avoid the latter. Per-test divergence is awkward.

**How to apply it.**
```kotlin
class TestGameDealsApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    startKoin {
      androidContext(this@TestGameDealsApplication)
      modules(productionModules + testOverrides)
    }
  }
}

val testNetworkOverridesModule = module {
  single<HttpClient> { mockHttpClient(get()) { respond("[]", HttpStatusCode.OK) } }
}
```

**Seen in.**
- app/src/androidTest/java/pm/bam/gamedeals/TestGameDealsApplication.kt
- app/src/androidTest/java/pm/bam/gamedeals/KoinTestRunner.kt
- app/src/androidTest/java/pm/bam/gamedeals/di/TestDatabaseOverridesModule.kt
- app/src/androidTest/java/pm/bam/gamedeals/di/TestNetworkOverridesModule.kt
- app/src/androidTest/java/pm/bam/gamedeals/di/TestImageLoaderOverridesModule.kt

### Shared fixtures DSL in `testing/commonMain`

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every test source set across commonTest, androidHostTest, androidDeviceTest

**The pattern.**
Small top-level builder functions create domain models with sensible defaults — `deal(id = "1", title = "X", salePrice = 9.99)`, `store(id = 1, name = "Steam")`, `game(...)`, `giveaway(...)`, `favouriteGame(...)`, `gameInfo(...)`, `dealDetails(...)`. Named parameters let each test override only the fields that matter. No object mothers, no factory classes, no inheritance — just top-level builder functions per type, grouped one file per model.

**Why this works for us.**
Avoids 50-line literal constructions per test. Defaults stay realistic, so tests don't accidentally exercise empty-string / zero-value paths unintentionally. Works in any source set because the builders live in `commonMain`.

**Known trade-offs / when it strains.**
Builders drift if domain models add required fields without sensible defaults — adding a non-nullable field to `Deal` requires updating the builder. Discoverability depends on file naming convention.

**How to apply it.**
```kotlin
// in testing/src/commonMain/kotlin/.../fixtures/DealFixtures.kt
fun deal(
  id: String = "1",
  title: String = "Portal 2",
  salePrice: Double = 4.99,
  normalPrice: Double = 9.99,
): Deal = Deal(id, title, salePrice, normalPrice, …)

// in a test
val cheap = deal(salePrice = 1.99)
val premium = deal(id = "2", normalPrice = 59.99)
```

**Seen in.**
- testing/src/commonMain/kotlin/pm/bam/gamedeals/testing/fixtures/DealFixtures.kt
- testing/src/commonMain/kotlin/pm/bam/gamedeals/testing/fixtures/StoreFixtures.kt
- testing/src/commonMain/kotlin/pm/bam/gamedeals/testing/fixtures/GameFixtures.kt
- testing/src/commonMain/kotlin/pm/bam/gamedeals/testing/fixtures/GiveawayFixtures.kt
- testing/src/commonMain/kotlin/pm/bam/gamedeals/testing/fixtures/FavouriteFixtures.kt

### Backtick Test Names

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every test function

**The pattern.**
Test functions use backtick names that read like sentences: `` `mapDelayAtLeast pads to delayMillis when transform is instant` ``, `` `initially loading state` ``, `` `load store deals from source failure` ``. No camelCase, no `given/when/then` prefix, no underscores.

**Why this works for us.**
Backtick names are more readable than camelCase and more flexible than rigid prefixes. They render well in JUnit runners and IDE outline views.

**Known trade-offs / when it strains.**
Long descriptions (20+ words) hurt scannability. Kotlin-specific style; surprising to Java-only developers. IDE click-to-run on a backtick name is occasionally flaky.

**How to apply it.**
```kotlin
@Test
fun `mapDelayAtLeast pads to delayMillis when transform is instant`() = runTest { … }

@Test
fun `load store deals from source failure`() = runTest { … }
```

**Seen in.** all test files

## What we don't do

- **No Turbine or `Flow.test()` helpers.** The custom `observeEmissions()` covers the codebase's needs without an extra testing dependency. **Why we avoid it:** the existing pattern is sufficient for current ViewModel + repository surface; adding Turbine would duplicate functionality.
- **No Kotest, AssertJ, or Hamcrest.** `kotlin.test` (commonTest) and JUnit 4 `Assert` (androidHostTest) plus Mokkery `verify` is the toolchain. **Why we avoid it:** assertions here are scalar; an assertion DSL would be ceremony without payoff.
- **No MockK in commonTest.** Mokkery 3.3.0 is the KMP mocking library; MockK is JVM-only and breaks Native compilation. MockK may persist in androidHostTest where Native portability isn't required, but new tests should default to Mokkery for uniformity.
- **No MockWebServer.** HTTP responses go through Ktor `MockEngine` via the `MockHttpClient` factory. **Why we avoid it:** MockWebServer is JVM-only and binds real sockets; the Ktor approach is KMP-portable and hermetic.
- **No `@get:Rule val mainCoroutineRule = MainCoroutineRule()`.** JUnit 4 rules don't run on Kotlin/Native. Extend `MainDispatcherTest` instead.
- **No Hilt in tests.** Koin overrides loaded last-wins via `TestGameDealsApplication`. **Why we avoid it:** Hilt was removed from the production graph; tests follow.
- **No per-test Koin `loadKoinModules` / `unloadKoinModules` reloads.** The override graph is layered once at app startup. **Why we avoid it:** per-test reload churn introduces ordering hazards and obscures which bindings are live for a given test.
- **No JSON fixture files on disk.** HTTP responses are in-process handler lambdas in each test; data fixtures are builder functions in `testing/commonMain/.../fixtures/`. **Why we avoid it:** on-disk fixtures drift silently from the API; inline strings or builders surface drift in code review.
- **No base test classes beyond `MainDispatcherTest`.** Tests compose behavior via `@BeforeTest`/`@AfterTest` hooks and the shared infrastructure in `:testing`. **Why we avoid it:** keeps tests modular and independently readable; the dispatcher seam is the only shared concern worth a base class.
- **No parameterized / data-driven tests.** All tests are discrete `@Test` functions. **Why we avoid it:** verbose but explicit; parameterized failure diagnostics are weaker.
- **No `testTag` on production composables.** See [ui-testing.md](ui-testing.md). **Why we avoid it:** test-only constants leak into other tests as hardcoded strings, couple tests to the screen's internal structure, and add nothing for users. Tests find nodes by visible text, content description, or role.

## Decommissioned

### Shared Coroutine Rule + Custom Flow Observation

**Status:** deprecated — replaced 2026-05-18 by the `MainDispatcherTest` abstract base class (see active pattern). `MainCoroutineRule` was a JUnit 4 rule and couldn't run on Kotlin/Native; `observeEmissions()` survives as part of the replacement entry.
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** every unit test class touching coroutines or Flows

**The pattern.**
Every unit test class declares `@get:Rule val mainCoroutineRule = MainCoroutineRule()`, which sets `Dispatchers.Main` to a `TestDispatcher` (default `UnconfinedTestDispatcher`). A custom `observeEmissions(coroutineScope, testDispatcher)` extension function launches a collection job on the test dispatcher and returns a mutable list filled asynchronously as the Flow emits. Tests call it with `this.backgroundScope` (from `runTest`) to observe state/events without blocking or missing emissions.

**Why this works for us.**
`UnconfinedTestDispatcher` greedy-executes jobs immediately, so observer and subject jobs run synchronously inside `runTest`, eliminating flakiness. `backgroundScope` cancels automatically at test end. No external testing-library dependency; the shape is small and auditable.

**Known trade-offs / when it strains.**
The mutable list collects every historical emission, so tests assert on indices (`first()`, `last()`, `size`). Rapidly emitting flows make order-sensitive assertions fragile. There is no `awaitItem()` / timeout semantics; all assertions are post-hoc.

**How to apply it.**
```kotlin
@get:Rule
val mainCoroutineRule = MainCoroutineRule()

@Test
fun test_example() = runTest {
  val vm = MyViewModel(fakeRepo)
  val states = vm.uiState
    .observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)

  assertEquals(1, states.size)
  assertEquals(expectedState, states.first())
}
```

**Seen in.**
- testing/src/main/java/pm/bam/gamedeals/testing/MainCoroutineRule.kt
- testing/src/main/java/pm/bam/gamedeals/testing/utils/TestingExtensions.kt
- feature/home/src/test/java/pm/bam/gamedeals/feature/home/ui/HomeViewModelTest.kt
- feature/game/src/test/java/pm/bam/gamedeals/feature/game/ui/GameViewModelTest.kt

### MockK Everywhere; No Hand-Rolled Fakes

**Status:** deprecated — replaced 2026-05-18 by Mokkery 3.3.0 in commonTest (see active pattern). MockK is JVM-only and incompatible with the Kotlin/Native targets introduced during the KMP migration. MockK may persist in androidHostTest until those tests migrate.
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all unit tests

**The pattern.**
Tests use `mockk()` with `every { … } returns …` and `coEvery { … } returns …` for all collaborators: repositories, DAOs, APIs, Clock, Logger. No hand-rolled fakes except where the interface is tiny (e.g., `MutableClock` for `CachedResource` boundary logic). Behavior is verified via `coVerify(exactly = N) { … }` and `verify { … }`.

**Why this works for us.**
MockK is compact and expressive; `coEvery` / `coVerify` integrate seamlessly with suspend functions and coroutines. Tests focus on behavior, not on maintaining fake state.

**Known trade-offs / when it strains.**
MockK shadows real behavior; a test can pass against a mock that would fail against the real implementation (e.g., missing null-checks, type mismatches). For complex state machines or contract-heavy integrations, hand-rolled fakes can be more trustworthy. The `MutableClock` exception in `CachedResourceTest` shows the boundary: pure stateless logic with a single dependency is simpler with a fake than a mock.

**How to apply it.**
```kotlin
private val repo: MyRepository = mockk()

@Test
fun test_refresh() = runTest {
  coEvery { repo.fetch() } returns listOf(item1)
  coEvery { repo.save(any()) } just runs

  impl.refresh()

  coVerify(exactly = 1) { repo.fetch() }
  coVerify(exactly = 1) { repo.save(item1) }
}
```

**Seen in.**
- feature/home/src/test/java/pm/bam/gamedeals/feature/home/ui/HomeViewModelTest.kt
- domain/src/test/java/pm/bam/gamedeals/domain/repositories/games/GamesRepositoryTest.kt
- domain/src/test/java/pm/bam/gamedeals/domain/repositories/cache/CachedResourceTest.kt

### MockWebServer for HTTP Contract Tests

**Status:** deprecated — replaced 2026-05-18 by `MockHttpClient` (Ktor `MockEngine`) (see active pattern). MockWebServer is JVM-only and binds real sockets; the KMP migration required a portable, in-process HTTP fake.
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** remote source tests (e.g., `CheapsharkSourceImplTest`)

**The pattern.**
Tests that exercise HTTP clients stand up a real `MockWebServer`, configure canned JSON responses, and assert that the client constructs the correct request (path, query parameters) and decodes the response correctly. The HTTP layer is not mocked; the real Retrofit + JSON serialization + error transformation runs end-to-end against the server. Helpers (currency formatter, date formatter) are mocked.

**Why this works for us.**
HTTP wiring (endpoint paths, query params, JSON parsing) is easy to get wrong and hard to debug in production. MockWebServer provides hermetic, deterministic fixtures — the API contract is verified without hitting the real backend.

**Known trade-offs / when it strains.**
Maintaining fixture JSON adds overhead; contract changes mean both fixture and test updates. MockWebServer is slower than an in-memory mock (still fast). Conditional or dynamic responses are awkward.

**How to apply it.**
```kotlin
private lateinit var mockWebServer: MockWebServer

@Before
fun setUp() {
  mockWebServer = MockWebServer().apply { start() }
  val retrofit = Retrofit.Builder()
    .baseUrl(mockWebServer.url("/").toString())
    .build()
  impl = CheapsharkSourceImpl(…, retrofit.create(DealsApi::class.java), …)
}

@Test
fun `fetchStores`() = runTest {
  mockWebServer.enqueue(MockResponse().setBody(STORE_JSON))

  val result = impl.fetchStores()

  val recorded = mockWebServer.takeRequest()
  assertEquals("/api/1.0/stores", recorded.path)
  assertEquals(1, result.size)
}
```

**Seen in.**
- remote/cheapshark/src/test/java/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImplTest.kt

### Fixture-Driven Hilt + Compose Integration Tests

**Status:** deprecated — replaced 2026-05-18 by the Koin test module override pattern + `MockHttpClient` (see active patterns). Hilt was removed from the production graph during the Koin migration; the JSON-fixture-on-disk approach was retired in favor of in-process handler lambdas.
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** end-to-end journey tests and screen tests in `:app` and `feature/*`

**The pattern.**
Instrumented tests use Hilt to inject real repositories, in-memory Room databases, and per-vendor `MockWebServer` instances. A `FixtureMockDispatcher` routes inbound HTTP requests to JSON fixture files in `androidTest/assets/fixtures/`. Compose UI tests use `createComposeRule()` / `createEmptyComposeRule()`, find elements by tag, and simulate user interactions. Screen-level tests mock the ViewModel; full integration tests stand up the Activity and the entire DI graph.

**Why this works for us.**
Fixtures are checked into version control and don't drift. The path-to-fixture mapping is declarative and easy to audit. Full integration tests catch wiring bugs (Hilt bindings, schema mismatches, navigation issues) that unit tests miss; screen-level tests stay fast.

**Known trade-offs / when it strains.**
Fixture maintenance is overhead; contract changes require regeneration. Full integration tests need an emulator/device and are slower to debug. Conditional fixtures (return A vs B by query param) require complex dispatcher logic.

**How to apply it.**
```kotlin
@UninstallModules(RemoteModule::class, DatabaseModule::class)
@HiltAndroidTest
class HomeToStoreToDealJourneyTest {
  @Inject @CheapShark
  lateinit var cheapShark: MockWebServer

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @Before
  fun setUp() {
    hiltRule.inject()
    scenario = ActivityScenario.launch(MainActivity::class.java)
  }

  @Test
  fun home_to_store_happy_path() {
    composeRule.waitUntil { /* element appears */ }
    composeRule.onNodeWithText("Steam").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Deal: Portal 2", substring = true)
      .performClick()
  }
}
```

Node finders follow the [ui-testing](ui-testing.md) policy — no `testTag` in production composables or instrumented tests.

**Seen in.**
- app/src/androidTest/java/pm/bam/gamedeals/integration/HomeToStoreToDealJourneyTest.kt
- app/src/androidTest/java/pm/bam/gamedeals/integration/support/FixtureMockDispatcher.kt
- feature/home/src/androidTest/java/pm/bam/gamedeals/feature/home/ui/HomeScreenTest.kt
