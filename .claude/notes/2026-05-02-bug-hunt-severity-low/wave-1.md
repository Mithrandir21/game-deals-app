# Wave 1 — 2026-05-02

**Summary:** 2 issues attempted, 2 PRs opened, 0 failed.

## Issues

### #78 → PR #81 — `fix(#78): use MutableStateFlow.update {} for atomic state mutations`
- Branch: `wave/2026-05-02-bug-hunt-severity-low/issue-78-stateflow-update`
- Files: `HomeViewModel.kt`, `GiveawaysViewModel.kt`
- Tests: none added; existing tests pass against `update {}` semantics.
- Surprises:
  - In `GiveawaysViewModel.reloadGiveaways`, the LOADING transition moved from a flow `.emit` into a side-effect `_uiState.update { ... }` — flow now emits nothing, so the builder was type-pinned to `flow<Unit>` to keep `logFlow` inference happy. Downstream `.collect { }` is intentionally empty.
  - Success-path `.collect { _uiState.emit(it) }` calls **not** converted: those emit a full state replacement (not a field-level merge), so no read-modify-write race exists there. Only LOADING/ERROR transitions changed.

### #79 → PR #82 — `fix(#79): annotate GiveawaySearchParameters @Immutable + migrate to ImmutableList`
- Branch: `wave/2026-05-02-bug-hunt-severity-low/issue-79-giveaway-search-immutable`
- Files: `domain/build.gradle.kts`, `domain/.../Giveaway.kt`, `GiveawaysRepositoryTest.kt`, `GiveawaysScreen.kt`, `GiveawaysViewModelTest.kt`
- Tests: 2 existing tests updated to `persistentListOf(...)` constructors; no new tests.
- Surprises:
  - `GiveawaySearchParameters` is `@Serializable` and used by `parametersSaver` in `GiveawaysScreen.kt`. kotlinx-serialization 1.9.0 supports `ImmutableList` natively — no custom serializer required, saver round-trip still works.
  - `:domain` was already on the `gamedeals.android.library.compose` convention plugin, so `@Immutable` import resolves without further gradle changes.
  - **Worktree workflow hiccup (recoverable):** the agent's first round of edits accidentally landed in the main repo (still on `dev`, no commits). The agent reverted those with `git checkout --` and re-applied to the worktree. Verified post-hoc: main repo working tree matches the start-of-conversation state (only `M .claude/lessons.md` + untracked `.claude/notes/`, `.claude/skills/github-issue-waves/`).

## Conflicts deferred

- **#80** — GameDetails.deals → ImmutableList. Conflicts with #79 on `domain/build.gradle.kts` (both add `kotlinx-collections-immutable` to `:domain`). Re-queue after PR #82 merges.

## Sanity-check results

| PR  | state | base  | head                        | commits ahead of dev |
|-----|-------|-------|-----------------------------|----------------------|
| #81 | OPEN  | `dev` | issue-78-stateflow-update   | 1                    |
| #82 | OPEN  | `dev` | issue-79-giveaway-search-immutable | 1             |

Both pass: `done`.

## Anomaly noted

The orchestrator's bash cwd shifted into one of the spawned worktrees (`agent-ab705b51`) at some point during this wave — possibly an artefact of how `isolation: "worktree"` affects the parent shell's state. All file writes used absolute paths so no data was misplaced; the main repo and its working tree are intact. Worth keeping an eye on next wave: prefer absolute paths and verify cwd before running `git status` / `git checkout` in the orchestrator.
