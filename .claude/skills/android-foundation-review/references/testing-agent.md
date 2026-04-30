# Testing Agent — Review Checklist & Rubric

You are evaluating the project's testing strategy, infrastructure, and actual test quality.
Good architecture without tests is just an intention. Tests are the proof that the
architecture works and the contract that keeps it honest.

**Out of scope for you:**
- ViewModel design and layering (you cover whether they're *tested*) → Layering & Separation
- Compose adoption details → Modern Patterns (you cover Compose *testing* specifically)
- Macrobenchmark and Baseline Profile generation → Performance (overlap is fine; flag
  if missing and let synthesis dedupe)

---

## 1. Test Inventory & Coverage Shape

**What to look for:**
- Ratio of test files to production files.
- Distribution shape: pyramid (many unit, fewer integration, fewest UI) is the goal.
  Ice cream cone (mostly UI, few unit) or hourglass (no integration) are smells.
- Modules or layers with **zero** tests.
- For KMP projects: `commonTest` coverage in addition to `androidTest`/`iosTest`.

**How to investigate:**
```bash
echo "=== Unit tests (test/) ==="
find . -path "*/src/test/*" -name "*.kt" 2>/dev/null | wc -l
echo "=== Common tests (commonTest/) ==="
find . -path "*/commonTest/*" -name "*.kt" 2>/dev/null | wc -l
echo "=== Instrumented tests (androidTest/) ==="
find . -path "*/src/androidTest/*" -name "*.kt" 2>/dev/null | wc -l
echo "=== Production source files ==="
find . -path "*/src/main/*" -name "*.kt" 2>/dev/null | wc -l
find . -path "*/commonMain/*" -name "*.kt" 2>/dev/null | wc -l

# Tests per module
for module_dir in $(find . -maxdepth 3 -name "build.gradle.kts" -exec dirname {} \; 2>/dev/null | grep -v "/build/" | sort -u); do
  unit=$(find "$module_dir" -path "*/test/*" -name "*.kt" 2>/dev/null | wc -l)
  android=$(find "$module_dir" -path "*/androidTest/*" -name "*.kt" 2>/dev/null | wc -l)
  prod=$(find "$module_dir" -path "*/main/*" -o -path "*/commonMain/*" 2>/dev/null | grep "\.kt$" | wc -l)
  if [ "$prod" -gt 0 ]; then
    echo "$module_dir: prod=$prod unit=$unit androidTest=$android"
  fi
done

# Untested ViewModels
echo "=== Untested ViewModels ==="
for vm in $(find . -name "*ViewModel.kt" -path "*/main/*" 2>/dev/null); do
  base=$(basename "$vm" .kt)
  test_count=$(find . -name "${base}Test.kt" -o -name "${base}Spec.kt" 2>/dev/null | wc -l)
  if [ "$test_count" -eq 0 ]; then
    echo "UNTESTED: $vm"
  fi
done | head -15
```

**Grading:**
- STRONG: Test-to-production ratio > 0.5, pyramid shape, every module has tests,
  ViewModels and use cases tested, KMP `commonTest` populated when applicable.
- ADEQUATE: Tests exist for core business logic but many ViewModels/repositories
  untested. Some modules have zero tests.
- WEAK: Test ratio < 0.2, mostly UI tests with few unit tests, large gaps in
  business-logic coverage.
- MISSING: Fewer than 10 test files in the entire project, or no test infrastructure.

---

## 2. Unit Test Quality

**What to look for:**
- Test naming: behavior-describing (`should emit error state when network fails`) vs
  method-mirroring (`testGetUser`).
- Assertions: specific and meaningful — not just `assertNotNull`. Modern projects use
  Truth, Kotest, Strikt, or `assertk` for fluent assertions.
- Structure: Arrange-Act-Assert or Given-When-Then, applied consistently.
- Behavior vs implementation: do tests verify what the code does
  ("emits loading then success") or how ("calls repository.get exactly once")?
  The former survives refactoring; the latter doesn't.
- Test independence: any shared mutable state between tests?

**How to investigate:**
```bash
# Sample 5 random test files
for test in $(find . -path "*/test/*" -name "*Test.kt" 2>/dev/null | sort -R 2>/dev/null | head -5 || find . -path "*/test/*" -name "*Test.kt" 2>/dev/null | head -5); do
  echo "========================================"
  echo "=== $test ==="
  echo "========================================"
  cat "$test" | head -80
done

# Naming patterns
grep -rn "fun \`" --include="*.kt" $(find . -path "*/test/*" -type d 2>/dev/null | head -5) 2>/dev/null | wc -l
grep -rn "fun test[A-Z]" --include="*.kt" $(find . -path "*/test/*" -type d 2>/dev/null | head -5) 2>/dev/null | wc -l

# Assertion library
grep -rn "import com.google.common.truth\|import io.kotest\|import strikt\|import assertk\|import org.junit.Assert" \
  --include="*.kt" $(find . -path "*/test/*" -type d 2>/dev/null | head -5) 2>/dev/null | sort -u | head -10
```

**Grading:**
- STRONG: Backtick names, behavior-focused assertions, consistent structure, modern
  assertion library, tests readable as documentation.
- ADEQUATE: Tests exist and pass, names are `testX`-style, basic JUnit assertions,
  some implementation-detail testing.
- WEAK: Tests are brittle — tightly coupled to implementation, heavy mocking of
  internals, no clear structure.
- MISSING: No meaningful unit tests.

---

## 3. Test Doubles Strategy

**What to look for:**
- Mocking framework: MockK (Kotlin-native), Mockito-Kotlin, Mockative (KMP), or
  hand-written fakes.
- Interfaces enable test doubles. Fakes (hand-written implementations of interfaces)
  are generally more maintainable for repositories and data sources than mocks.
  Mocks are fine for verifying interactions but overuse → brittle tests.
- Shared test fixtures or builders (`TestUserFactory`, `FakeUserRepository`).
- Gradle `testFixtures()` feature: a clean way to share fakes across modules.

**How to investigate:**
```bash
echo "MockK:"
grep -rn "import io.mockk\|mockk<\|every {" --include="*.kt" . 2>/dev/null | wc -l
echo "Mockito:"
grep -rn "import org.mockito\|mock(\|whenever(\|verify(" --include="*.kt" . 2>/dev/null | wc -l
echo "Mockative (KMP):"
grep -rn "io.mockative\|@Mock" --include="*.kt" --include="*.toml" . 2>/dev/null | wc -l

# Hand-written fakes
find . \( -name "Fake*.kt" -o -name "*Fake.kt" -o -name "Stub*.kt" \) 2>/dev/null | grep -v "/build/" | head -15

# Shared fixtures
find . \( -path "*/test/*" -o -path "*/testFixtures/*" \) \
  \( -name "*Factory*.kt" -o -name "*Builder*.kt" -o -name "*Fixture*.kt" \) 2>/dev/null | head -10

# testFixtures usage
grep -rn "testFixtures(\|java-test-fixtures\|testFixtures =" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: Fakes for data-layer interfaces, mocks used sparingly for interaction
  verification, shared fixtures via `testFixtures()` or a `:test-utils` module.
- ADEQUATE: MockK/Mockito throughout but tests aren't overly brittle. Some fakes for
  key interfaces.
- WEAK: Everything mocked, including data classes. Tests break on any refactor.
  No shared fakes.
- MISSING: No test doubles — no tests, or "unit tests" use real implementations.

---

## 4. Coroutine & Flow Testing

**What to look for:**
- `kotlinx-coroutines-test` used. `runTest` is the standard test coroutine builder.
- `Dispatchers.Main` properly replaced via `Dispatchers.setMain` or a JUnit Rule.
  Even better: production code uses an injected dispatcher, eliminating the need.
- **Turbine** (`app.cash.turbine`) for Flow assertions. Manual collection is brittle.
- Time-based tests use `advanceTimeBy` / `advanceUntilIdle`, never `Thread.sleep`
  or unscheduled `delay`.

**How to investigate:**
```bash
grep -rn "runTest\|TestDispatcher\|StandardTestDispatcher\|UnconfinedTestDispatcher" \
  --include="*.kt" . 2>/dev/null | head -10

echo "Turbine usage:"
grep -rn "app.cash.turbine\|\.test\s*{" --include="*.kt" --include="*.toml" . 2>/dev/null | head -10

echo "Bad: Thread.sleep in tests:"
grep -rn "Thread.sleep" --include="*.kt" $(find . -path "*/test/*" -type d 2>/dev/null) 2>/dev/null | head -10

echo "MainDispatcherRule pattern:"
grep -rn "setMain\|MainDispatcherRule\|MainCoroutineRule" --include="*.kt" . 2>/dev/null | head -10

echo "runBlocking in tests (legacy):"
grep -rn "runBlocking" --include="*.kt" $(find . -path "*/test/*" -type d 2>/dev/null) 2>/dev/null | head -10
```

**Grading:**
- STRONG: `runTest` consistently, TestDispatcher replaces Main (or injected dispatchers
  remove the need), Turbine for Flows, `advanceTimeBy` for timing.
- ADEQUATE: `runTest` used but no Turbine (manual Flow collection). Some tests handle
  Main dispatcher, others don't.
- WEAK: `runBlocking` instead of `runTest`, no dispatcher replacement, `Thread.sleep`,
  Flows not tested.
- MISSING: No coroutine-specific test infrastructure.

---

## 5. UI / Compose Testing

**What to look for:**
- Compose test deps: `compose-ui-test-junit4`, `compose-ui-test-manifest`.
- `composeTestRule` / `createComposeRule` / `createAndroidComposeRule`.
- What's tested: state rendering (given state, verify UI) AND interactions (click →
  state → assertion).
- **Robolectric + Compose** for fast unit-style Compose tests (no emulator), or
  `runComposeUiTest` (Compose Multiplatform).
- **Screenshot testing**: Paparazzi, Roborazzi, or Android Studio's built-in screenshot
  testing (Preview-based).
- Semantic-tree assertions over `testTag` as the primary strategy (semantics also serves
  accessibility — see Modern Patterns).
- For View-based UIs: Espresso tests, idling resources.

**How to investigate:**
```bash
grep -rn "compose.*test\|ui-test-junit4\|ui-test-manifest" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5

grep -rln "composeTestRule\|createComposeRule\|createAndroidComposeRule\|runComposeUiTest" --include="*.kt" . 2>/dev/null | head -10

echo "Screenshot testing:"
grep -rn "paparazzi\|roborazzi\|com.android.compose.screenshot\|@PreviewScreenshotTest" \
  --include="*.kts" --include="*.toml" --include="*.kt" . 2>/dev/null | head -10

echo "Robolectric:"
grep -rn "org.robolectric\|@RunWith(RobolectricTestRunner\|@Config" --include="*.kt" --include="*.toml" . 2>/dev/null | head -5

# Espresso (legacy)
grep -rn "Espresso\|onView\|withId\|ViewMatchers" --include="*.kt" . 2>/dev/null | head -5

# Sample a Compose test
for test in $(grep -rln "composeTestRule" --include="*.kt" . 2>/dev/null | head -2); do
  echo "=== $test ==="
  cat "$test" | head -40
done
```

**Grading:**
- STRONG: Compose tests cover state + interactions, screenshot tests guard visual
  regressions (Paparazzi/Roborazzi/AS Screenshot Testing), Robolectric used for fast
  Compose unit tests, semantic tree is the assertion surface.
- ADEQUATE: Some Compose tests, low coverage, no screenshot testing.
- WEAK: Only Espresso in a Compose project, or UI tests exist but are flaky/ignored.
- MISSING: No UI testing.

---

## 6. Integration & End-to-End Testing

**What to look for:**
- Tests that exercise the full stack: UI → ViewModel → Repository → fake API.
- Test DI configuration: `HiltTestApplication`, `@CustomTestApplication`,
  `@HiltAndroidTest`, or Koin `loadKoinModules` for swapping in fakes.
- API contract tests via MockWebServer (OkHttp) or WireMock.
- Database migration tests using `MigrationTestHelper` (Room).

**How to investigate:**
```bash
grep -rn "HiltTestApplication\|@CustomTestApplication\|@HiltAndroidTest" --include="*.kt" . 2>/dev/null | head -10
grep -rn "MockWebServer\|WireMock\|mockwebserver" --include="*.kt" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
grep -rn "MigrationTestHelper\|migration" --include="*.kt" $(find . -path "*/test/*" -o -path "*/androidTest/*" 2>/dev/null) 2>/dev/null | head -10
find . -name "*TestModule*" -o -name "*FakeModule*" 2>/dev/null | head -5
```

**Grading:**
- STRONG: Integration tests with test DI, MockWebServer for API contracts, Room migration
  tests, end-to-end coverage of critical journeys.
- ADEQUATE: Some integration tests (Repository + Room), but no API contract or migration
  tests.
- WEAK: Only unit tests; or integration tests don't use proper doubles (hitting real APIs).
- MISSING: No integration or E2E tests.

---

## 7. Static Analysis & Code Quality Gates

**What to look for:**
- **detekt** for Kotlin static analysis. Configured rules, baseline file, integrated
  into the build.
- **ktlint** (or **Spotless** wrapping ktlint) for formatting.
- **Android Lint** with custom rules (or at least baseline + abort-on-error in CI).
- **Konsist** for architecture rules-as-code (e.g., "no class in `:domain` may import
  from `androidx`").
- **Dependency analysis** (e.g., `dependency-analysis-gradle-plugin`) catching unused
  or misplaced dependencies.

**How to investigate:**
```bash
grep -rn "detekt\b" --include="*.kts" --include="*.toml" --include="*.gradle" . 2>/dev/null | head -10
find . -name "detekt.yml" -o -name "detekt-config.yml" 2>/dev/null | head -3

grep -rn "ktlint\|spotless" --include="*.kts" --include="*.toml" --include="*.gradle" . 2>/dev/null | head -10

grep -rn "abortOnError\|disable\s*+=\s*\|checkReleaseBuilds" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -10

grep -rn "konsist\|com.lemonappdev" --include="*.kts" --include="*.toml" --include="*.kt" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: detekt + ktlint/Spotless enforced, Lint with `abortOnError`, Konsist for
  architecture rules, dependency-analysis plugin.
- ADEQUATE: detekt OR ktlint, basic Lint config, no architecture rules.
- WEAK: One tool present but not enforced (warnings only, never blocks PRs).
- MISSING: No static analysis.

---

## 8. CI / Coverage / Test Infrastructure

**What to look for:**
- CI configuration (GitHub Actions, Bitrise, CircleCI, Jenkins).
- CI runs unit + instrumented tests on every PR. Instrumented tests via emulator
  (Gradle Managed Devices is the modern way) or Firebase Test Lab.
- Coverage tool: **Kover** (Kotlin-native) or JaCoCo. Thresholds enforced?
- Test parallelization, test sharding for large suites.
- Flaky test detection / quarantine.

**How to investigate:**
```bash
find . \( -name "*.yml" -path "*/.github/workflows/*" -o -name "*.yaml" -path "*/.github/workflows/*" -o -name "Jenkinsfile" -o -name "bitrise.yml" \) 2>/dev/null | head -10

# Sample CI configs
for ci in $(find . -path "*/.github/workflows/*.yml" 2>/dev/null | head -3); do
  echo "=== $ci ==="
  cat "$ci" | head -80
done

echo "Coverage tooling:"
grep -rn "org.jetbrains.kotlinx.kover\|jacoco\|kover\b" --include="*.kts" --include="*.toml" --include="*.yml" . 2>/dev/null | head -10

echo "Gradle Managed Devices:"
grep -rn "managedDevices\|ManagedVirtualDevice" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: CI runs unit + instrumented on every PR, coverage tracked with Kover/JaCoCo
  and enforced thresholds, static analysis blocks merges, GMD or Firebase Test Lab for
  emulator tests.
- ADEQUATE: CI exists and runs tests, no coverage tracking or threshold enforcement.
- WEAK: CI only builds, doesn't run tests; or CI is broken/skipped.
- MISSING: No CI configuration.
