---
**Path scope:** `testing/**`, `*/src/test/**`, `*/src/androidTest/**`
**Last surveyed:** f215235 on 2026-05-14
---

# Testing

This codebase keeps a deliberate split between JVM unit tests and instrumented tests across three integration tiers: domain (repositories with MockK + virtual time), feature (ViewModels observing state via custom Flow collectors), and network (MockWebServer for API contract). The `:testing` module ships shared infrastructure (`MainCoroutineRule`, an `observeEmissions()` Flow collector) that enforces unconfined dispatchers and uses `TestScope.backgroundScope` for observation. No Turbine — a custom collector drains Flows into mutable lists. Naming uses backtick style.

The **Compose UI test finder policy** (how to locate nodes in instrumented `*ScreenTest.kt` files, why `testTag` is forbidden, when to add `Modifier.semantics`) is documented separately in [ui-testing.md](ui-testing.md).

## Patterns

### Shared Coroutine Rule + Custom Flow Observation

**Status:** established
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

**Status:** established
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

### Virtual Time Discipline via `testScheduler.currentTime`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
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
- common/src/test/java/pm/bam/gamedeals/common/FlowExtensionsTest.kt

### JUnit 4 Assertions + MockK Verify; No Assertion Framework

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all unit tests

**The pattern.**
Tests import `org.junit.Assert.*` (`assertEquals`, `assertTrue`, `assertThrows`, `assertNull`, `assertNotNull`) and use MockK's `coVerify` / `verify` for behavior. No AssertJ, Hamcrest, or Kotest. Assertions are simple and scannable.

**Why this works for us.**
JUnit 4 assertions are built-in; no extra dependency. `coVerify` integrates directly with MockK. Most assertions here are scalar (size, equality, boolean), so the trade-off is acceptable.

**Known trade-offs / when it strains.**
JUnit assertions have terse error messages compared to AssertJ or Hamcrest. For complex multi-field comparisons, an assertion library would shine.

**How to apply it.**
```kotlin
assertEquals(expectedValue, actualValue)
assertTrue(condition)
assertThrows(ExceptionType::class.java) { block() }
coVerify(exactly = 1) { repo.fetch() }
```

**Seen in.**
- feature/home/src/test/java/pm/bam/gamedeals/feature/home/ui/HomeViewModelTest.kt
- domain/src/test/java/pm/bam/gamedeals/domain/repositories/deals/DealsRepositoryTest.kt

### Room + `InstantTaskExecutorRule` for Synchronous DAO Tests

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** repository tests that exercise DAOs

**The pattern.**
Tests interacting with Room DAOs declare `@get:Rule val instantExecutorRule = InstantTaskExecutorRule()` to force DAO operations onto the test thread, bypassing Architecture Components' default thread-hopping. MockK mocks the DAO and Database, and the test verifies the repository constructs queries, stamps data (e.g., expiry), and calls the DAO in the right order. Real Room usage is confined to integration tests that stand up an in-memory database.

**Why this works for us.**
Synchronous execution makes unit tests deterministic and fast. `InstantTaskExecutorRule` is lightweight. Mocking the DAO keeps the test focused on repository logic (retry, cache expiry, transactionality) without depending on schema or Room's async machinery.

**Known trade-offs / when it strains.**
DAO mocks don't catch schema mismatches or query bugs; those surface only in integration tests. For repositories that interact with multiple DAOs or rely on Room's query scheduling, mocking can obscure bugs.

**How to apply it.**
```kotlin
@get:Rule
val instantExecutorRule = InstantTaskExecutorRule()

private val dao: MyDao = mockk()

@Test
fun test_refresh_with_cache_expiry() = runTest {
  val expired = Deal(expires = now - 10_000)
  coEvery { dao.get(id) } returns listOf(expired)

  impl.refresh(id)

  coVerify { dao.clear(id) }
  coVerify { source.fetch(id) }
}
```

**Seen in.**
- domain/src/test/java/pm/bam/gamedeals/domain/repositories/deals/DealsRepositoryTest.kt
- domain/src/test/java/pm/bam/gamedeals/domain/repositories/games/GamesRepositoryTest.kt

### MockWebServer for HTTP Contract Tests

**Status:** established
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

**Status:** established
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

### Backtick Test Names

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
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
- **No Kotest, AssertJ, or Hamcrest.** JUnit 4 `Assert` + MockK `coVerify` is the toolchain. **Why we avoid it:** assertions here are scalar; an assertion DSL would be ceremony without payoff.
- **No base test classes / shared test superclasses.** Every test declares `MainCoroutineRule` individually. **Why we avoid it:** keeps tests modular and independently readable; the small amount of duplication is acceptable.
- **No `androidTest` coverage in `:domain`, `:common`, or `:remote`.** Integration coverage lives in `:app` and feature modules. Paging mediators are tested via MockK with hand-rolled slot capture, which works but is less elegant than real Room test fixtures.
- **No parameterized / data-driven tests.** All tests are discrete `@Test` functions. **Why we avoid it:** verbose but explicit; parameterized failure diagnostics are weaker.
- **No test data builders or object mothers.** Mocked objects are created ad-hoc. **Why we avoid it:** MockK keeps construction terse; builders would add maintenance without much gain at the current scale.
- **No `testTag` on production composables.** See [ui-testing.md](ui-testing.md). **Why we avoid it:** test-only constants leak into other tests as hardcoded strings, couple tests to the screen's internal structure, and add nothing for users. Tests find nodes by visible text, content description, or role.
