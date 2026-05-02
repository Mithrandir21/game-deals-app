# Wave 1 — 2026-05-02-bug-hunt-2

**2 issues attempted · 2 succeeded · 0 failed**

Selected from `gh issue list --state open --label bug-hunt` after the
`2026-05-02-bug-hunt-severity-medium` hunt filed #90, #91, #92 (and #92 was
closed before this campaign began).

## Issues

### #90 — DealDetailsController.load doesn't cancel prior load

- **PR:** [#94](https://github.com/Mithrandir21/game-deals-android-app/pull/94)
- **Branch:** `wave/2026-05-02-bug-hunt-2/issue-90-deal-details-viewmodel-cancel`
- **Status:** open
- **Fix summary:** Added `private var loadJob: Job?` + `loadJob?.cancel()` before relaunch in `DealDetailsViewModel`. Mirrors `HomeViewModel.loadTopStoresDeals` (PR #33) and `DealDetailsController.load` (commit `b64307b`).
- **Surprises:** Controller path was already fixed on `dev` HEAD via commit `b64307b` (filed by issue #71 / PR #84); the planner caught this in Step 2c and we scoped the fix to `DealDetailsViewModel` only. The agent reported the fix and test were "already committed locally on this worktree's branch (`2012966`) when picked up" — likely a stale local checkout of the worktree branch from a prior partial run. Verified content, ran tests, pushed.

### #91 — withMinimumDuration uses System.currentTimeMillis

- **PR:** [#93](https://github.com/Mithrandir21/game-deals-android-app/pull/93)
- **Branch:** `wave/2026-05-02-bug-hunt-2/issue-91-with-minimum-duration-virtual-time`
- **Status:** open
- **Fix summary:** Replaced wall-clock `System.currentTimeMillis()` padding with `launch { delay(delayMillis) }` + `pad.join()` inside `coroutineScope`. Direct mirror of the pattern PR #59 applied to `mapDelayAtLeast` / `flatMapLatestDelayAtLeast`. `inline`/`crossinline` preserved. Two new unit tests verify virtual-time padding and no over-padding when block exceeds delayMillis.
- **Surprises:** None of consequence. Worktree `gradlew` lacked execute bit; agent ran via `bash ./gradlew` with `JAVA_HOME` from local-JDK memory note.

## Conflicts deferred out of this wave

None — only 2 candidates and they have disjoint file sets.

## Sanity-check results

| PR | Branch on origin | Commits ahead of dev | PR state | Base |
|---|---|---|---|---|
| #94 | yes | 1 | OPEN | `dev` |
| #93 | yes | 1 | OPEN | `dev` |

Both pass.
