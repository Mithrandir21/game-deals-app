# Shared Bug Report Format

Every Android bug-hunt specialist writes findings in this exact format. The dispatcher relies on it for aggregation, deduplication, and ranking. Specialists invoked standalone (without the dispatcher) should also use this format.

## Finding template

```markdown
### BUG-{NNN}: {Short title}

| Field | Value |
|---|---|
| **Severity** | Critical / High / Medium / Low |
| **Category** | e.g. "Memory leak", "Race condition", "Lifecycle violation" |
| **Location** | `path/to/File.kt:LINE` (or `Class.method`) |
| **Effort** | Trivial / Small / Medium / Large |
| **Confidence** | High / Medium / Low |

**Description.** One to three sentences stating exactly what is wrong.

**Impact.** What happens at runtime. Be concrete: "Activity is retained after rotation,
leaking ~4MB per rotation"; "Crashes with `IllegalStateException` when user backgrounds
the app during checkout"; "ANR after ~3s when network is slow on cold start".

**Evidence.**
```kotlin
// minimal snippet (≤ 15 lines) demonstrating the antipattern
```

**Recommended fix.** A concrete change. Show before/after when helpful, or describe the
exact API/pattern to use instead.

**Confidence rationale.** Why this is (or might not be) a real bug. Note any context
that would change the verdict.
```

## Severity rubric

- **Critical** — crashes in normal use, data loss, security breach, ANRs reachable on common code paths, leaks that grow unboundedly with normal usage.
- **High** — leaks bounded but significant, races that produce wrong state intermittently, broken features for a meaningful subset of users, missing cancellation that leaks coroutines after lifecycle end.
- **Medium** — performance issues, sporadic incorrect behaviour under specific conditions, resource leaks bounded to a single screen.
- **Low** — minor inefficiency, latent issue that is not currently reachable but would become a bug under foreseeable changes.

## Effort rubric

- **Trivial** — under 30 minutes, single-file change, no behaviour change to test.
- **Small** — under 4 hours, clear local pattern fix, light testing.
- **Medium** — 1–2 days, refactor across a few files, needs new tests.
- **Large** — more than 2 days, architectural or cross-module change.

## Confidence rubric

- **High** — well-known antipattern with clear runtime evidence; would surface in code review.
- **Medium** — likely a bug, but depends on calling context the analyzer cannot fully see.
- **Low** — suspicious; flag for human judgment. Always pair with an explicit rationale.

## Writing discipline

- Only report things you can point to with concrete `file:line` evidence.
- Don't pad with stylistic suggestions or architectural opinions.
- If unsure something is a real defect: mark Confidence Low and explain what context could make it a false positive. Don't omit; don't inflate.
- Prefer fewer high-confidence findings to many speculative ones.
