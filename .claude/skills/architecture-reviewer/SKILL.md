---
name: architecture-reviewer
description: Senior-engineer-grade review of an Android or KMP codebase's architecture — modularization, layering, DI graph, error handling, navigation, state management, and the Android/KMP split. Use whenever the user asks for an "architecture review", "code review", "audit my project", "review my Android app", "what's wrong with this codebase", or "tech debt assessment". Produces a written report with severity-ranked findings the dev can use to plan work.
---

# Architecture Reviewer

A structured pass over an Android/KMP codebase to surface architectural issues a senior engineer would flag in a thorough code review. Not a linter — opinions and severity, with reasoning.

## When to use

Triggers: "review my project", "architecture audit", "what's wrong", "tech debt", "should we refactor", "is this code good".

For a single PR review, just review it. Use this skill when the scope is a module or whole app.

## Process

### Phase 1: Inventory

Before forming opinions, get the lay of the land. Run quick checks:

```
find . -name "build.gradle*" | wc -l        # module count
find . -name "*.kt" | wc -l                  # rough size
cat settings.gradle.kts                      # module graph
cat gradle/libs.versions.toml                # dependencies + versions
```

Note:
- Total modules and their grouping (feature/data/core/...).
- Kotlin / AGP / Compose Compiler / Gradle versions.
- DI framework, navigation library, networking stack, persistence layer.
- Test directories present? Instrumented or unit only?

### Phase 2: Read across, not down

Don't read every file. Pick a representative slice:

- The settings + version catalog.
- One full feature module top-to-bottom (UI → ViewModel → use case → repository → data source).
- The DI graph entry points.
- The main `Application` class.
- One test from each layer if they exist.

Spend more time on patterns that repeat than on one-off oddities.

### Phase 3: Evaluate against axes

Score each axis as **Good / Mixed / Concerning** and note the evidence:

**Modularization**
- Are modules cohesive? Do feature modules avoid depending on each other?
- Is there a clear core/data/feature split, or is everything in `:app`?
- Are there cyclic dependencies (Gradle catches these but they can still hint at design smell)?

**Layering**
- Are UI, domain (if present), and data clearly separated?
- Do ViewModels talk to repositories directly, or through use cases? Is that consistent?
- Does data leak into UI (e.g. Retrofit response DTOs used in Composables)?

**State management**
- One pattern (StateFlow + UiState data class) or many?
- Is state hoisted properly in Compose?
- Are events handled consistently (single `onEvent(Event)`, vs. many lambdas, vs. mixed)?

**DI**
- Hilt or Koin used consistently, or hand-rolled in places?
- Scoping correct (singleton vs. activity vs. viewmodel)?
- Are bindings co-located with implementations, or all in one giant module?

**Error handling**
- Repositories return `Result<T>`, sealed `Outcome`, or throw?
- Are exceptions translated at layer boundaries?
- Does UI handle loading / empty / error states explicitly, or only happy path?

**Concurrency**
- Coroutines used consistently, or mixed with RxJava / callbacks?
- Dispatchers injected or hardcoded?
- Any `runBlocking` or `GlobalScope`?

**Testing**
- Unit tests for ViewModels and repos? UI tests for critical screens?
- Are tests using fakes, mocks, or both? Consistently?
- Anything in `@Ignore` or quarantined?

**Tooling**
- Convention plugins (`build-logic`) used, or copy-pasted Gradle?
- Version catalog (`libs.versions.toml`) in use?
- Lint and detekt configured? Baseline files maintained?
- Compose Compiler metrics emitted?

**KMP (if applicable)**
- Clear `commonMain` / `androidMain` / `iosMain` split?
- iOS-facing API uses platform-friendly types (no raw `Flow`, no generics with variance)?
- SKIE or KMP-NativeCoroutines used for Flow/suspend interop, or hand-rolled?

### Phase 4: Rank findings

For each finding, assign:

- **Severity**: Blocker (breaks correctness / security / scalability) / High (significant friction) / Medium (smell, future cost) / Low (style).
- **Effort**: S (hours) / M (days) / L (weeks).
- **Risk**: change-of-touching-it (Low/Med/High).

Prefer findings that are High severity + Low effort + Low risk — those are the wins to pitch first.

### Phase 5: Write the report

Use this structure:

```
# Architecture Review: [App / Module]

## Summary
[2–3 sentences: overall health, top 3 things to address.]

## Inventory
- Modules: N
- Stack: [Kotlin X, AGP Y, Compose Z, ...]
- Patterns observed: [list]

## Strengths
[3–5 bullets — things to keep. Reviews that only list problems get ignored.]

## Findings

### [Severity] [Short name]
**What**: One paragraph.
**Why it matters**: One paragraph.
**Fix**: Concrete change, with effort and risk.

[Repeat for each finding, grouped by severity.]

## Recommended sequence
1. [Quick win]
2. [Medium]
3. [Larger refactor, only if 1–2 land first]

## Out of scope
[Things you noticed but didn't dig into, so the dev knows the gap.]
```

## Output

A markdown report following the structure above. Aim for thorough but readable — a senior engineer should be able to skim in 10 minutes and find their next move.

## Common pitfalls

- **Reading everything.** You'll run out of context. Pick a slice and trust patterns.
- **Listing only problems.** Demotivating and inaccurate. Note the strengths too.
- **"You should use clean architecture."** Empty advice. Tie every recommendation to a specific cost the team is paying now.
- **Severity inflation.** If everything is High, nothing is. Reserve Blocker for things that actively break.
- **Recommending a rewrite.** Almost never the right call. Look for the smallest cut that buys the most.
