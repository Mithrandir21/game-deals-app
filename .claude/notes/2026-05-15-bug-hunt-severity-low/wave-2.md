# Wave 2 — 2026-05-15

Campaign: `2026-05-15-bug-hunt-severity-low` (labels: `bug-hunt`, `severity:low`)
Base branch: `dev`

## Summary
- Attempted: 2
- Succeeded: 2
- Failed: 0

## Per-issue

### #151 — Sentry.init runs on Main during Application.onCreate (dormant)
- **PR:** #164 — https://github.com/Mithrandir21/game-deals-android-app/pull/164
- **Title:** `refactor(app): dispatch Sentry.init off Main via applicationScope`
- **Files changed (1 modified):** `app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt` (+16/-3).
- **Approach:** wrapped `Sentry.init { ... }` body in `applicationScope.launch { ... }`, reusing the `CoroutineScope(SupervisorJob() + Dispatchers.IO)` field hoisted by #153 (PR #160). Kept the `if (SENTRY_DSN.isEmpty()) return` early-return at the top so debug/CI builds skip the scope dispatch entirely. No try/catch needed — `SupervisorJob` isolates failures from siblings (`warmDomainDatabase`). Added a KDoc on `initSentry()` describing the contract.
- **Validation:** `:app:compileDebugKotlin` BUILD SUCCESSFUL with JBR 21.
- **Notes:** `applicationScope` field's KDoc explicitly anticipated this exact use case — dependency closed cleanly.

### #152 — httpClient default-arg on expect lost to Swift
- **PR:** #165 — https://github.com/Mithrandir21/game-deals-android-app/pull/165
- **Title:** *(pending — check PR for actual title)*
- **Files changed (1 modified):** `remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/HttpClientFactory.kt`.
- **Approach:** added a `commonMain`-only `fun httpClient(): HttpClient = httpClient {}` overload (plus KDoc explaining the Swift-interop motivation). The `expect` declaration and both Android/iOS actuals are untouched.
- **Validation:** `:remote:compileDebugKotlinAndroid`, `:remote:compileKotlinIosSimulatorArm64`, `:remote:testDebugUnitTest` all green.
- **Notes:** Surfaced a pre-existing deprecation warning in `common/.../DatetimeParsingImpl.kt` (`dayOfMonth` → `day`) — unrelated to this PR; left alone.

## Conflicts deferred
None. Both issues touched disjoint modules (`:app` vs `:remote`) with no shared files.

## Sanity-check results
- #151 (#164): branch on origin (✓), 1 commit ahead of dev (✓), OPEN against dev (✓).
- #152 (#165): branch on origin (✓), 1 commit ahead of dev (✓), OPEN against dev (✓).

## Campaign status after this wave

Once these 2 PRs merge, ALL 9 open severity:low bug-hunt issues will be resolved:
- Wave 1 (merged): #147, #149, #150, #153
- Wave 2 (open): #151, #152
- Relabeled out: #148 (moved to `tech-debt` + `area:kmp`)

Campaign will be marked `complete` on next re-entry.
