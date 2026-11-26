# Campaign lessons

- (wave 1) Issue #90 — `DealDetailsController.load` was already fixed on `dev` (b64307b) before this campaign started. The planner sub-agent caught this by reading `dev` HEAD; without that step the worker would have either no-op'd on the controller or duplicated work. Pattern: planners should always verify against the merge target, not the wave branch HEAD.
- (wave 1) Issue #91 — `withMinimumDuration` was the missing companion to PR #59 (which fixed `mapDelayAtLeast` / `flatMapLatestDelayAtLeast` for the same `System.currentTimeMillis` antipattern). Worth grepping for any remaining wall-clock helpers; the same bug class likely surfaced once more in a less-trafficked corner.

# Promotion candidates (project-wide)

- [x] **Wall-clock time in suspend helpers breaks test virtual time.** Promoted as `L-2026-05-02-09` on 2026-05-02.
- [x] **Worker agents should re-verify the issue against `origin/<merge-target>` HEAD before starting work.** Promoted as `L-2026-05-02-10` on 2026-05-02.
