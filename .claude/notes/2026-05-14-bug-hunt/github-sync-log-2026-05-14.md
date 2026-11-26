# GitHub Sync Log — 2026-05-14 12:55

Source report: `.claude/bug-hunt-workspace/android-bug-hunt-report.md`
Merge target: `origin/dev` @ `9783ad4`
Plan: `.claude/bug-hunt-workspace/github-sync-plan.md`

## Actions

- BUG-001 → filed as #143 — https://github.com/Mithrandir21/game-deals-android-app/issues/143  (severity:high, effort:small)
- BUG-002 → filed as #144 — https://github.com/Mithrandir21/game-deals-android-app/issues/144  (severity:high, effort:small)
- BUG-003 → regression candidate; commented on closed #38 (https://github.com/Mithrandir21/game-deals-android-app/issues/38#issuecomment-4450448848) — sibling defect, not a regression; per plan default (comment-only)
- BUG-004 → filed as #145 — https://github.com/Mithrandir21/game-deals-android-app/issues/145  (severity:medium, effort:small, area:compose; ambiguous, defaulted to file new)
- BUG-005 → filed as #146 — https://github.com/Mithrandir21/game-deals-android-app/issues/146  (severity:medium, effort:small, area:coroutines; included `L-2026-05-06-03` Note on Dispatchers.IO commonMain rules)
- BUG-006 → regression candidate; commented on closed #128 (https://github.com/Mithrandir21/game-deals-android-app/issues/128#issuecomment-4450449160) — partial-fix follow-up; per plan default (comment-only)
- BUG-007 → filed as #147 — https://github.com/Mithrandir21/game-deals-android-app/issues/147  (severity:low, effort:small, area:compose; cross-references closed #80 and `L-2026-05-02-02`)
- BUG-008 → filed as #148 — https://github.com/Mithrandir21/game-deals-android-app/issues/148  (severity:low; no effort/area labels per plan)
- BUG-009 → filed as #149 — https://github.com/Mithrandir21/game-deals-android-app/issues/149  (severity:low, effort:trivial, area:coroutines; ambiguous, defaulted to file new)
- BUG-010 → filed as #150 — https://github.com/Mithrandir21/game-deals-android-app/issues/150  (severity:low, effort:small; no area label)
- BUG-011 → regression candidate; commented on closed #130 (https://github.com/Mithrandir21/game-deals-android-app/issues/130#issuecomment-4450449601) — direct regression of #130's accepted fix; per plan default (comment-only)
- BUG-012 → filed as #151 — https://github.com/Mithrandir21/game-deals-android-app/issues/151  (severity:low, effort:small)
- BUG-013 → filed as #152 — https://github.com/Mithrandir21/game-deals-android-app/issues/152  (severity:low, effort:small)
- BUG-014 → skipped (regression candidate with `## Default: SKIP silently` — finding's own recommended fix is "None required"; #30's accepted WebView teardown fix is in place)
- BUG-015 → filed as #153 — https://github.com/Mithrandir21/game-deals-android-app/issues/153  (severity:low, effort:trivial, area:coroutines)

## Tally

- New issues filed: **11** (#143–#153)
- Regression comments: **3** (#38, #128, #130)
- Skipped silently: **1** (BUG-014)
- Failures: **0**

## Label warnings (carried over from plan)
- BUG-008 filed without `effort:*` label (report said "N/A informational")
- 6 findings filed without `area:*` label (BUG-001, BUG-002, BUG-008, BUG-010, BUG-012, BUG-013) — consider creating `area:kmp`, `area:ios`, `area:database` labels if these categories will recur

## Failures (RESUMABLE)
*(none)*
