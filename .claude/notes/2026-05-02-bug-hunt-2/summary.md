# Campaign summary — 2026-05-02-bug-hunt-2

**Status:** complete
**Created:** 2026-05-02
**Completed:** 2026-05-02
**Labels:** `bug-hunt`
**Total waves:** 1
**Total issues:** 2 attempted · 2 merged · 0 failed · 100% success

## Context

Continuation campaign after `2026-05-02-bug-hunt` (waves 1–2, issues #71–#77) completed.
Picked up issues filed by the `android-bug-hunting-github-sync` skill following the
`2026-05-02-bug-hunt-severity-medium` hunt:

- #90 — DealDetailsController.load doesn't cancel prior load
- #91 — withMinimumDuration uses System.currentTimeMillis
- #92 — Room setQueryCallback Executor never shut down (closed before this campaign began)

Slug suffix `-2` was used because the same date + label combination already existed as a
completed campaign folder.

## Waves

### Wave 1 (2026-05-02)

| Issue | Title | PR | Status |
|---|---|---|---|
| #90 | DealDetailsController.load doesn't cancel prior load | [#94](https://github.com/Mithrandir21/game-deals-android-app/pull/94) | merged |
| #91 | withMinimumDuration uses System.currentTimeMillis | [#93](https://github.com/Mithrandir21/game-deals-android-app/pull/93) | merged |

Both issues had disjoint file sets (`feature/deal/.../DealDetailsViewModel.kt` vs
`common/.../FlowExtensions.kt`) and ran in parallel without conflict.

## Lessons promoted to project-wide

- `L-2026-05-02-09` — Wall-clock time in suspend helpers breaks test virtual time
- `L-2026-05-02-10` — Re-verify findings against `origin/<merge-target>` HEAD before working them

## Notable

- Issue #90's `DealDetailsController` portion was already fixed on `dev` (commit
  `b64307b`) before this campaign started — the planner sub-agent caught this and
  scoped the worker to `DealDetailsViewModel` only. This is the canonical case behind
  `L-2026-05-02-10` and behind Step 2.5 of the `android-bug-hunting-github-sync` skill
  (added 2026-05-02 in the same session).
- Issue #91 was the missed companion to PR #59 (issue #45) — the same wall-clock
  antipattern that PR #59 fixed in `mapDelayAtLeast` / `flatMapLatestDelayAtLeast`
  was still present in `withMinimumDuration`. Worth running a periodic grep for new
  wall-clock-in-suspend helpers if the codebase grows more "minimum duration" /
  "settle" / "throttle" utilities.

## Unresolved failures

None.
