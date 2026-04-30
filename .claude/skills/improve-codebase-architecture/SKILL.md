---
name: improve-android-architecture
description: Explore an Android codebase to find architectural improvement opportunities, focusing on testability via module deepening and alignment with modern Android guidance (Compose, UDF, single-activity, modularization, version catalogs, KSP, Hilt/Koin). Use when the user wants to improve architecture, find refactoring opportunities, consolidate tightly-coupled modules, modernize toward current Android defaults, or make the codebase more AI-navigable. Outputs RFC-style GitHub issues for chosen refactors.
---

# Improve Android Architecture

Explore an Android codebase, surface architectural friction, and propose module-deepening refactors aligned with modern Android guidance. Output is a GitHub issue RFC per chosen refactor.

A **deep module** (Ousterhout, *A Philosophy of Software Design*) has a small interface hiding a large implementation. Deep modules are easier for AI tools to navigate, easier to test at the boundary instead of inside, and survive internal refactors without breaking callers.

This skill is opinionated about *how* to evaluate friction, but stays neutral on choices the project has already made (Hilt vs Koin, Compose vs a Compose+Views mix, MVI vs plain UDF). It pushes toward modern Android defaults only when the existing code is fighting against them.

KMP is in scope as a secondary concern — when a candidate touches `commonMain`, treat KMP as one more axis of the design, not the primary lens.

## Process

### 1. Explore the codebase

Use the Agent tool with `subagent_type=Explore` to navigate naturally. Don't follow rigid heuristics — explore, then note where you experience friction. The friction is the signal.

**Generic friction signals**

- Understanding one concept requires bouncing between many small files.
- A module's interface is nearly as wide as its implementation (shallow module).
- Pure functions extracted purely "for testability" while the real bugs hide in how they're called.
- Tight coupling that creates integration risk in the seams.
- Untested or hard-to-test code paths.

**Android-specific friction signals**

- `UseCase` / `Interactor` classes that forward a single repository call with no logic.
- `Repository` interface plus a single `RepositoryImpl`, where the interface exists only for mocking in tests.
- DTO ↔ Domain ↔ UI model triples connected by trivial `Mapper` classes.
- `ViewModel` that delegates to "managers" or "controllers" that are thin wrappers around something else.
- Multiple `StateFlow`s on a screen that are always `combine`d into one UI state — should be one state model.
- Per-screen MVI / reducer scaffolding when the screen has no real state machine.
- Composables driven directly by raw flows from the data layer instead of a screen state model.
- DI graph wiring scattered across many overlapping `@Module` / Koin modules.
- Gradle modules so small they're effectively packages in disguise (no real boundary, no separate testability story).
- Logic split between `:domain` and `:data` modules where one type is used in both directions.
- `Context`, `Resources`, `Intent`, or other framework types leaking into domain or business logic.

**Modernization signals (raise only if the code is fighting current defaults)**

- XML layouts inflated and bridged into Compose via `AndroidView` for screens that should just be Compose.
- `LiveData` and `Flow` mixed in the same screen with manual conversion.
- RxJava with `kotlinx-coroutines-rx*` adapters used as a permanent bridge.
- KAPT for processors that have KSP equivalents (Hilt, Room, Moshi).
- Hardcoded versions in module `build.gradle.kts` files instead of a `libs.versions.toml` catalog.
- Per-module Gradle boilerplate that convention plugins (in an included build) would eliminate.
- Multiple `Activity`s for what should be Compose destinations under a single Activity.
- Pre-`androidx.navigation.compose` 2.8 string-route navigation when type-safe routes are available.
- `runBlocking` in production code paths where structured concurrency is expected.

The friction you encounter IS the signal. Don't manufacture candidates from a checklist.

### 2. Present candidates

Numbered list. For each candidate:

- **Cluster** — modules / files involved, with paths
- **Why they're coupled** — shared types, call patterns, co-ownership of a concept
- **Dependency category** — see `REFERENCE.md`
- **Test impact** — what existing tests get replaced by boundary tests, what becomes cheaper to test
- **Modernization angle** — if the deepening also moves the code toward a current Android default, name it (one line)

Do NOT propose interfaces yet. Ask the user: *"Which of these would you like to explore?"*

### 3. User picks a candidate

### 4. Frame the problem space

Before spawning sub-agents, write a user-facing explanation of the problem space for the chosen candidate:

- Constraints any new interface must satisfy — lifecycle, threading / dispatcher, Compose recomposition behavior, configuration changes, process death and `SavedStateHandle` restoration if relevant.
- Dependencies it relies on and their category (`REFERENCE.md`).
- A rough illustrative code sketch — *not a proposal*, just a way to ground the constraints.

Show this to the user, then immediately proceed to Step 5. The user reads and thinks while sub-agents work in parallel.

### 5. Design multiple interfaces in parallel

Spawn 3+ Agent sub-agents in parallel. Each must produce a **radically different** interface for the deepened module. Brief each separately with file paths, coupling details, dependency category, and what's being hidden. The brief is independent of the user-facing explanation in Step 4.

Give each agent a different design constraint:

- **Agent 1 — Minimal surface:** 1–3 entry points max. Hide everything reasonable.
- **Agent 2 — Maximum flexibility:** extension points, composition, multiple use cases.
- **Agent 3 — Common case trivial:** optimize the default path; advanced cases can be ugly.
- **Agent 4 — Ports & adapters** *(if the module crosses a network or process boundary):* clean port at the boundary, in-memory adapter for tests, real adapter for production.
- **Agent 5 — `commonMain`-first** *(if KMP code is in scope):* maximize what lives in `commonMain`; push platform-specific code behind `expect` / `actual` only where unavoidable.

Each sub-agent outputs:

1. Interface signature (types, methods, params, `suspend` / `Flow` markers).
2. Usage example from a caller (ViewModel, Composable, WorkManager worker — whatever's realistic).
3. What complexity is hidden internally.
4. Dependency strategy (`REFERENCE.md` categories).
5. Test plan — which test type runs at the boundary (`REFERENCE.md` table).
6. Trade-offs.

Present designs sequentially, then compare them in prose.

After comparing, give your own recommendation: which design is strongest and why. If elements from different designs would combine well, propose a hybrid. Be opinionated — the user wants a strong read, not a menu.

### 6. User picks an interface (or accepts the recommendation)

### 7. Write the RFC

Use the issue template in `REFERENCE.md`. Output as Markdown ready to paste into a GitHub issue. Keep it focused on *one* refactor — if scope is creeping, surface that and offer to split into multiple issues.
