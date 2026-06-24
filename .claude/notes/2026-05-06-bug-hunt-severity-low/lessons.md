# Campaign lessons — 2026-05-06-bug-hunt-severity-low

## Campaign lessons

- **(wave 1) Issue #125 — `pinnedScrollBehavior` is itself `@Composable`; allocation cause is the default `canScroll` lambda, not the wrapper.** The original bug-hunt finding said "wrap `TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())` in `remember(topAppBarState)`." That doesn't compile (`@Composable` invocation outside a `@Composable` context). The real cause: `pinnedScrollBehavior` already does `remember(state, canScroll)` internally; the default `canScroll = { true }` param is a fresh lambda each recomposition, invalidating the `remember` key. Real fix: hoist `canScroll` into a stable `remember { { true } }`. **See promotion candidate.**

- **(wave 1) Issue #129 — SharedFlow → StateFlow swap loses re-tap-with-same-value semantic.** `MutableSharedFlow(replay=1, DROP_OLDEST)` re-fires on identical re-emission; `MutableStateFlow` conflates. The issue body's recommended fix (switch to MutableStateFlow + `.update`) is correct for the *bug* (RMW race) but quietly changes a downstream semantic. Today's single call site only emits on param changes, so the conflation is harmless — but future "re-tap-to-retry-same-search" UX would silently not work. Generalization: **when the bug-hunt fix proposes swapping SharedFlow → StateFlow (or vice versa), check whether any downstream relies on the changed re-emission semantic.**

- **(wave 1) Issue #128 — `androidx.annotation` not on commonMain classpath.** AndroidX annotation isn't in this codebase's commonMain dependency surface; the catalog has no entry for the KMP fork either. So `@WorkerThread` (and friends) can only land on Android-side impls. For #128 that's the correct scope anyway — the contract is Android-specific (`SharedPreferences.commit`).

- **(wave 1 — process) The batched-PR shape worked well for low-severity sweeps.** User explicitly requested 6 commits / 1 branch / 1 PR for the latent-foot-gun batch. Net-of-review effort was clearly lower than 6 separate PRs. Worth considering as a first-class option in `/github-issue-waves` for "fix all" sweeps where the issues share a quality tier (Low/Low) and the reviewer would otherwise approve the batch in one pass.

## Promotion candidates (project-wide)

- [x] **L-2026-05-06-05: `TopAppBarDefaults.pinnedScrollBehavior` allocates per recomposition via the default `canScroll` lambda, not the wrapper** — promoted to `.claude/lessons.md` on 2026-05-06.

  Tentative full body:
  ```
  L-2026-05-06-05 · `TopAppBarDefaults.pinnedScrollBehavior` allocates per recomposition via the default `canScroll`, not the wrapper
  Status: active · Confidence: confirmed · Tags: compose, recomposition, top-app-bar, material3, stable-lambda
  Applies to: any code calling `TopAppBarDefaults.pinnedScrollBehavior(...)` (or
  `enterAlwaysScrollBehavior`, `exitUntilCollapsedScrollBehavior`) directly inside a
  composable body.

  `TopAppBarDefaults.pinnedScrollBehavior(state, canScroll)` is itself `@Composable`
  and already does `remember(state, canScroll) { PinnedScrollBehavior(...) }`
  internally. So you cannot wrap it in a caller-side `remember(state) {
  TopAppBarDefaults.pinnedScrollBehavior(state) }` — that fails with "Composable
  invocations can only happen from the context of a Composable function."

  The actual cause of per-recomposition allocation is the default
  `canScroll = { true }` parameter — a fresh lambda each recomposition,
  which invalidates Material3's internal remember key. Hoist `canScroll` into a
  stable `remember { { true } }` (or pass `canScroll = remember { { true } }`
  inline). Same applies to other Material3 helpers that take a default lambda
  parameter — check before assuming "wrap in `remember`" is the fix.

  Source: 2026-05-06-bug-hunt-severity-low batch (issue #125 / PR #134). The original
  bug-hunt finding misidentified the cause; worker investigated Material3 source to
  find the real one.
  ```

  This is a non-obvious project-wide insight that future Compose audits will keep
  re-flagging incorrectly without it. Recommend promoting.

- [ ] **(weaker candidate): When the bug-hunt fix proposes Flow-type swap (SharedFlow ↔ StateFlow), check downstream re-emission semantics.** This is a process tip about the bug-hunt → fix workflow rather than a code/architecture lesson; could be worded as a generalization of L-2026-05-02-07 (StateFlow conflation), or kept campaign-only. Recommend keeping campaign-only — it's a corollary, not a fresh lesson.
