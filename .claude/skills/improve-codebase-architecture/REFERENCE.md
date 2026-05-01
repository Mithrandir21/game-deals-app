# Reference

Companion to `SKILL.md`. Defines dependency categories for Android, the testing strategy that maps to each, and the issue template the RFC must follow.

## Dependency Categories

Classify each dependency before deepening. The category dictates the test type at the boundary.

### 1. In-process

Pure Kotlin computation, in-memory state, no I/O, no Android framework. Always deepenable — merge the modules and test directly with JVM unit tests.

Examples: domain logic, state reducers, formatters, validators, mappers, calculators, business rules.

### 2. Local-substitutable

Real implementation has a fast in-process test stand-in:

- **Room** → `Room.inMemoryDatabaseBuilder` with `setQueryExecutor(testDispatcher.asExecutor())`.
- **DataStore (Preferences / Proto)** → `PreferenceDataStoreFactory.create` or `DataStoreFactory.create` against a temp directory.
- **OkHttp / Retrofit** → `MockWebServer` or a custom `Interceptor` that returns canned responses.
- **WorkManager** → `WorkManagerTestInitHelper` with a synchronous executor.
- **Coroutines / Flow** → `runTest`, `TestDispatcher` (`StandardTestDispatcher` or `UnconfinedTestDispatcher`), `Turbine`.
- **Navigation Compose** → `TestNavHostController`.
- **`SavedStateHandle`** → construct directly with the initial map.
- **Paging 3** → `PagingSource.load()` invoked directly, `AsyncPagingDataDiffer` for flows.

The deepened module is tested with the stand-in inside JVM unit tests where possible. Use Compose UI tests when the boundary genuinely includes Composables.

### 3. Remote but owned (Ports & Adapters)

Your own services across a network or process boundary — your backend, a separate KMP module exposing a port, a sibling app process. Define an interface at the module boundary; the deep module owns the logic, the transport is injected. Production: HTTP / gRPC / IPC adapter. Tests: in-memory adapter.

Recommendation shape: *"Define a port interface; implement an HTTP adapter for production and an in-memory adapter for tests, so the logic can be tested as one deep module even though it's deployed across a network boundary."*

### 4. True external (Fake or Mock)

Third-party services you don't control: Firebase (Auth, Firestore, Remote Config, Crashlytics), Stripe, Mixpanel / Amplitude, ad SDKs, payment processors, Google Play Billing, Maps, ML Kit. Inject as a port at the boundary.

**Prefer fakes over mocks.** A fake implementation of the port survives refactors; a mock that asserts on call patterns calcifies them. Reach for a mocking framework (MockK) only when the surface is large enough that hand-rolling a fake is genuinely worse — which is rarer than people think.

### 5. Android framework (special case)

`Context`, `Resources`, `Intent`, `PackageManager`, `ConnectivityManager`, system services, sensors, permissions, location, notifications, `Activity` / `Fragment` lifecycle.

Don't let framework types leak into domain or business logic. Wrap each touched surface in a small port that exposes only what you actually need:

- `NetworkStatus` instead of `ConnectivityManager`.
- `ResourceProvider` (or pass `StringResource` IDs) instead of `Resources`.
- `IntentLauncher` instead of raw `Intent` construction.
- `PermissionChecker` instead of `ContextCompat.checkSelfPermission`.

Production adapter calls the framework. Test adapter is a plain Kotlin fake. The deep module stays a JVM unit test.

For things that must exercise the real framework (custom `View`s, OEM-specific behavior, real `WorkManager` execution, real permissions flow), use **instrumented tests** on a device or emulator — as a thin top layer, not a substitute for unit tests.

## Testing Strategy

Core principle: **replace, don't layer.**

- Old unit tests on shallow modules become waste once boundary tests exist — delete them.
- Write new tests at the deepened module's interface boundary.
- Tests assert on observable outcomes through the public interface, not internal state.
- Tests should survive internal refactors — they describe behavior, not implementation.
- Prefer fakes over mocks.
- If a deepening proposal moves tests *down* the table below (e.g., from instrumented to JVM unit), that's a strong signal the deepening is worth doing.

### Choosing the test type by boundary

Map the deepened module's boundary to the cheapest test type that still exercises real behavior. Defaults below stick to current Android-recommended tooling — no Robolectric, no Roborazzi.

| Boundary contains... | Test type | Tools |
|---|---|---|
| Pure Kotlin / coroutines / Flows | JVM unit test (`src/test`) | JUnit, `kotlinx-coroutines-test`, Turbine, Truth or AssertK or Kotest assertions |
| Room / DataStore / OkHttp behind a stand-in | JVM unit test (`src/test`) | in-memory Room, temp DataStore, MockWebServer |
| ViewModel + `StateFlow` / `SavedStateHandle` | JVM unit test (`src/test`) | `runTest`, `TestDispatcher`, Turbine on `uiState` |
| WorkManager workers' logic | JVM unit test (`src/test`) | invoke `doWork()` / `CoroutineWorker.doWork()` directly with fakes |
| Composable behavior (state, semantics, interaction) | Compose UI test (`src/androidTest`) | `createAndroidComposeRule` / `createComposeRule`, semantics matchers |
| Composable visual output | Screenshot test | Compose Preview Screenshot Testing (Google first-party) or Paparazzi |
| Navigation graph wiring | Compose UI test (`src/androidTest`) | `TestNavHostController` + `createAndroidComposeRule` |
| Hilt graph end-to-end | Instrumented test (`src/androidTest`) | `@HiltAndroidTest`, `HiltAndroidRule`, `HiltTestApplication` |
| Real device behavior (sensors, OEM, permissions, real WorkManager) | Instrumented test (`src/androidTest`) | AndroidX Test, Espresso *only* for legacy XML View screens |
| Startup, scrolling, baseline profile generation | Macrobenchmark | `androidx.benchmark.macro.junit4` |
| KMP `commonMain` logic | `commonTest` | `kotlin.test`, Turbine multiplatform, `kotlinx-coroutines-test` |

Default to the highest row that still covers the boundary. Drop down only when the boundary genuinely requires it.

### What goes where

- **Unit (JVM, `src/test`)** — domain logic, ViewModels, repositories with stand-ins, mappers, reducers, worker logic. The bulk of tests live here.
- **Compose UI (`src/androidTest`)** — Composable behavior with `createAndroidComposeRule` (or `createComposeRule` when no Activity is needed). Run on emulator or device.
- **Screenshot** — visual regression for Composables and design-system components, via Compose Preview Screenshot Testing or Paparazzi. Pick one; don't run two parallel screenshot stacks.
- **Instrumented (`src/androidTest`)** — only when the test genuinely needs the framework or a device. Hilt-wired end-to-end checks, custom `View`s, real `WorkManager` execution, sensors, permissions flows. Espresso is fine for legacy XML View screens that exist for compatibility.
- **Macrobenchmark** — startup, scrolling, baseline profile generation, R8 verification.

### Anti-patterns to avoid in the test plan

- Mocking your own types just to assert which methods were called — that's coupling tests to implementation.
- Adding a `Repository` interface with a single production implementation purely to enable mocking — inject the real type behind a fake-friendly port instead, or keep the type concrete and inject its dependencies.
- Writing instrumented tests for things that can be JVM unit tests with a port + fake.
- Asserting on `internal` state via `@VisibleForTesting` — assert on the public interface, or the boundary is wrong.
- Two screenshot stacks (Paparazzi + Compose Preview Screenshot Testing) running in CI on the same module.

## Issue Template

<issue-template>

## Problem

Describe the architectural friction:

- Which modules are shallow and tightly coupled (with paths).
- What integration risk exists in the seams between them.
- Why this makes the codebase harder to navigate, test, and maintain.
- If relevant: which modern Android default the current shape is fighting (one line).

## Proposed Interface

The chosen interface design:

- Interface signature (types, methods, params, `suspend` / `Flow` markers).
- Usage example showing how callers use it (ViewModel, Composable, Worker — whatever's realistic).
- Threading and lifecycle expectations: which dispatcher methods run on, who owns the `CoroutineScope`, how cancellation propagates, behavior across configuration change and process death.
- What complexity it hides internally.

## Dependency Strategy

Which category applies (see `REFERENCE.md`) and how dependencies are handled:

- **In-process**: merged directly.
- **Local-substitutable**: tested with [specific stand-in, e.g. in-memory Room].
- **Ports & adapters**: port definition + production adapter + test adapter.
- **External**: fake at the boundary; mock only if justified.
- **Android framework**: wrapped behind a port; fake adapter for unit tests; production adapter for the real framework call.

## Module / Package Layout

- Where the deepened module lives: Gradle module (`:feature:x`, `:core:y`) and package path.
- Visibility: which symbols are `internal` vs `public`.
- DI graph changes: Hilt `@Module` / `@InstallIn` scope, Koin module, or manual graph wiring.
- If Gradle modules change: which modules are merged, split, or renamed; impact on parallel build performance.
- Version catalog (`libs.versions.toml`) updates if new dependencies are introduced.
- Convention plugin updates if module conventions change.

## Testing Strategy

- **New boundary tests to write**: behaviors verified at the interface, with the test type from `REFERENCE.md` for each.
- **Old tests to delete**: shallow-module tests that become redundant.
- **Test environment needs**: stand-ins (in-memory Room, MockWebServer, etc.), fakes, Hilt test bindings, Compose test rule, screenshot baseline.
- **Net change**: tests moving from instrumented → JVM unit, or from many small unit tests → fewer boundary tests. Quantify if possible.

## Implementation Recommendations

Durable architectural guidance, NOT coupled to current file paths:

- What the module owns (responsibilities).
- What it hides (implementation details — DTOs, mappers, transport, caching).
- What it exposes (the interface contract — types, error model, threading guarantees).
- How callers migrate to the new interface (mechanical steps, deprecation path if needed).
- Backward-compatibility plan if other modules depend on the old shape.

## Out of Scope

What this RFC explicitly does *not* change. Important for keeping the diff focused and the review tractable.

</issue-template>
