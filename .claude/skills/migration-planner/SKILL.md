---
name: migration-planner
description: Plan large, multi-PR migrations on Android — Views→Compose, Dagger→Hilt, LiveData→Flow, RxJava→Coroutines, single-module→multi-module, Java→Kotlin, Groovy→KTS, AGP/Kotlin version bumps with breaking changes. Use whenever the user says "migrate", "switch from X to Y", "move to Compose", "kill RxJava", "we still have Java/Groovy", or asks how to phase a big technical change without freezing the rest of the team. Produces a phased plan with safety nets and rollback paths.
---

# Migration Planner

Big migrations fail when they try to be one PR, freeze the codebase, or have no rollback. This skill produces a plan that lets the team keep shipping while the migration runs in parallel.

## When to use

Triggers: "migrate to Compose", "drop RxJava", "Dagger to Hilt", "LiveData to Flow", "Java to Kotlin", "Groovy to KTS", "multi-module", "AGP 8 upgrade", "Kotlin 2.0 migration".

For one-file conversions, just do them. Use this skill when the change touches > 5 files or affects more than one team member's workflow.

## Process

### Phase 1: Define done

Be specific. "Migrate to Compose" is not a plan. Pick one:

- All new screens are Compose; existing Views stay until rewritten organically. (Soft migration.)
- All screens are Compose by [date]; no `XML` layouts remain. (Hard migration.)
- Compose is allowed inside `AndroidView`; full Compose for greenfield only. (Hybrid.)

Each has a different cost. Decide before planning.

### Phase 2: Pick a coexistence strategy

Most migrations work because the old and new can coexist for months. The strategy decides how:

| Migration | Coexistence mechanism |
|---|---|
| Views → Compose | `ComposeView` inside XML, `AndroidView` inside Compose. New screens fully Compose. |
| Dagger → Hilt | Hilt under the hood is Dagger; can run side-by-side. Hilt entry points (`@AndroidEntryPoint`) work in Activities/Fragments that still use Dagger components elsewhere. |
| LiveData → Flow | `Flow.asLiveData()` and `LiveData.asFlow()` bridge both directions. Migrate by leaf, not by root. |
| RxJava → Coroutines | `Single.await()`, `Observable.asFlow()` via `kotlinx-coroutines-rx3`. Convert at the boundary. |
| Groovy → KTS | Convert per file. No interop issues — Gradle reads both. |
| Java → Kotlin | Files interop natively. Convert per file, IDE-assisted. |
| Single-module → multi-module | Extract by feature, leave `:app` as glue. See `modularization-strategist`. |

### Phase 3: Identify boundaries and gotchas

Write down what breaks on contact:

- **Generics variance**: Kotlin's `out`/`in` aren't visible to Java. Watch when exposing Kotlin APIs to Java callers.
- **Backpressure**: RxJava's `Flowable` has a model Coroutines doesn't. For high-throughput flows, plan the `Flow.buffer()` / `conflate()` strategy.
- **Lifecycle semantics**: `LiveData` is lifecycle-aware out of the box; `StateFlow` needs `repeatOnLifecycle` or `collectAsStateWithLifecycle`. Don't lose this.
- **Dagger scopes**: Hilt has fixed scopes (`@Singleton`, `@ActivityScoped`, etc.). Custom Dagger scopes may need explicit mapping.
- **Compose interop**: `ComposeView` inside a `RecyclerView` item works but needs `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`. Without it: leaks.

### Phase 4: Phase the work

Aim for 3–5 phases. Each phase should be:

- Mergeable to main when done (no long-lived branch).
- Reversible without affecting users.
- Independent enough that a different person could pick it up.

Template:

**Phase 0 — Foundation**
- Add the new dependency, configure plugins, add convention plugin / lint rules.
- No behavior change. Should be a small, low-risk PR.

**Phase 1 — Bridge**
- Add adapters/conversions between old and new (`Flow.asLiveData()`, `ComposeView` setup, Hilt parallel to Dagger).
- Pick one small feature and convert it end-to-end as proof.

**Phase 2 — Scale out**
- Convert feature by feature. Each is its own PR.
- Add a CI rule that blocks new usage of the old pattern (lint, detekt custom rule, or grep in CI).

**Phase 3 — Remove the bridge**
- Once nothing uses old → new conversion adapters, delete them.
- Drop the old dependency.

**Phase 4 — Cleanup**
- Search for lingering imports, dead code, stale docs.
- Update onboarding docs and contributor guide.

### Phase 5: Safety nets

For each migration, decide:

- **Feature flag**: Can the new path be toggled off in production? Often worth the wiring for high-risk migrations (Compose screens, networking).
- **Telemetry**: Add crash-reporting tags so you can tell new-path crashes from old-path ones.
- **Tests**: Baseline current behavior in instrumented tests before changing implementation. Otherwise, you can't prove no regression.
- **Rollback**: At each phase, what's the rollback? "Revert the PR" only works for small phases.

### Phase 6: Communicate

Migrations fail socially as often as technically. Plan for:

- A team-wide doc with the why, the plan, and the timeline.
- A "what to do with new code starting today" rule. Without this, the old pattern keeps growing.
- A weekly check-in on progress and surprises.

## Output

A migration document:

```
# Migration: [from] → [to]

## Definition of done
[specific, measurable]

## Coexistence strategy
[how old and new run side-by-side]

## Phases
### Phase 0 — Foundation
- Goal:
- PR shape:
- Risk:

### Phase 1 — Bridge
...

[etc.]

## Safety nets
- Feature flag:
- Telemetry:
- Tests:
- Rollback per phase:

## Team rules during migration
- New code:
- Existing code touched:
- Old pattern allowed until:
```

## Common pitfalls

- **The "big bang" PR.** Six months of work, 400 files changed, impossible to review or revert. Always phase.
- **No "stop the bleeding" rule.** New code keeps using the old pattern while you migrate. Add a CI check.
- **Migrating from leaves to roots when roots dictate the API.** Pick the right direction per migration — sometimes leaves first (LiveData), sometimes roots (Dagger components).
- **No metrics.** Without telemetry tagging new vs. old, you can't tell whether the migration introduced regressions.
- **Forgetting the human cost.** Migrations are a tax on every PR review during the migration window. Communicate the timeline.
