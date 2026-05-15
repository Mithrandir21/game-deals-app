# Wave 1 — 2026-05-15

Campaign: `2026-05-15-bug-hunt-severity-low` (labels: `bug-hunt`, `severity:low`)
Base branch: `dev`

## Summary
- Attempted: 4
- Succeeded: 4
- Failed: 0

## Per-issue

### #147 — Pair<Store, GameDeal> makes wrapping list parameter unstable in Compose
- **PR:** #163 — https://github.com/Mithrandir21/game-deals-android-app/pull/163
- **Title:** `fix(compose-stability): replace Pair<Store, …> with @Immutable wrappers` (or similar — pending PR title check)
- **Approach:** Introduced two `@Immutable` wrapper data classes — `StoreDealPair(store, deal)` in `:feature:game/ui/` and `StoreCheaperStorePair(store, cheaperStore)` in `:common:ui/deal/`. Renamed `.first/.second` to `.store/.deal` (or `.store/.cheaperStore`) at all consumer sites including previews and tests.
- **Files changed (8 modified + 2 created):**
  - new: `feature/game/.../StoreDealPair.kt`, `common/ui/.../StoreCheaperStorePair.kt`
  - modified: GameViewModel.kt, GameScreen.kt, GameViewModelTest.kt, GameScreenTest.kt, DealBottomSheetData.kt, DealBottomSheet.kt, DealDetailsController.kt, DealBottomSheetTest.kt
- **Validation:** `:feature:game:testDebugUnitTest`, `:common:ui:testDebugUnitTest`, iOS-simulator compile, `:feature:game:compileDebugAndroidTestKotlinAndroid`, `:common:ui:compileDebugAndroidTestKotlinAndroid` — all green.
- **Notes:** `:feature:home` and `:feature:store` only reference `DealBottomSheetData.cheaperStores` via `emptyList()` in tests, which still type-checks against the new `List<StoreCheaperStorePair>` — no changes needed outside the in-scope modules. No build.gradle.kts changes (kotlinx-collections-immutable already exposed transitively). Straight refactor; no surprises.

### #149 — SharingStarted.Eagerly → WhileSubscribed(5_000) in GiveawaysViewModel
- **PR:** #162 — https://github.com/Mithrandir21/game-deals-android-app/pull/162
- **Title:** `refactor(giveaways): use WhileSubscribed(5_000) for uiState stateIn`
- **Approach:** One-line swap to match the prevailing convention (5 other VMs already use `WhileSubscribed(5_000)`).
- **Files changed (2 modified):** GiveawaysViewModel.kt, GiveawaysViewModelTest.kt.
- **Validation:** all 11 tests in `:feature:giveaways:testDebugUnitTest` pass; iOS-simulator compile green.
- **Notes:** **5 existing tests required adjustment** — the swap is NOT test-transparent:
  - **3 tests** (`initially_loading`, `initially_error`, `load_giveaways_with_search_parameters`) assumed `Eagerly`'s primed-upstream semantics: `emissions.size == 1` and `emissions.first()`. Under `WhileSubscribed`, the test collector sees the placeholder `GiveawaysScreenData()` before the upstream's first real emission lands → 2 emissions. Switched to `emissions.last()` and dropped `size == 1` assertions.
  - **2 reload tests** (`reload_giveaways`, `reload_giveaways_emits_LOADING_before_refresh_completes`) called `viewModel.reloadGiveaways()` *before* `observeStates(viewModel)`. With `Eagerly` that worked (upstream was already collecting); with `WhileSubscribed`, no subscriber means the `loadingFlow=true` write is never observed. Fix: subscribe first, then `reloadGiveaways()`. Also gated `refreshGiveaways()` with a `CompletableDeferred` so SUCCESS doesn't race in and clobber LOADING before the assertion runs.

### #150 — FavouritesRepository.toggleFavourite TOCTOU
- **PR:** #161 — https://github.com/Mithrandir21/game-deals-android-app/pull/161
- **Title:** `fix(domain): atomic toggleFavourite via DAO @Transaction`
- **Approach:** Moved the toggle into `FavouritesDao` as `@Transaction suspend fun toggleFavourite(...)` — atomic `SELECT EXISTS` + insert/delete in a single transaction. Repo delegates straight through; public signature unchanged.
- **Files changed (3 modified):** FavouritesDao.kt, FavouritesRepository.kt, FavouritesRepositoryTest.kt.
- **Validation:** `:domain:testDebugUnitTest`, `:domain:compileKotlinIosSimulatorArm64` — both green. Room KSP processor accepted `@Transaction` default method on a Kotlin interface on both Android and iOS with no special config.
- **Notes:** Sub-agent added an extra `dateAddedMs: Long` parameter to the new DAO method (not in the planner's literal three-arg signature) because the DAO has no access to `Clock`. Repo passes `clock.nowMillis()`. Public repo signature stayed identical so no feature-module call-site changes were needed.

### #153 — Unmanaged CoroutineScope for DB warm-up
- **PR:** #160 — https://github.com/Mithrandir21/game-deals-android-app/pull/160
- **Title:** `refactor(app): hoist application-scoped CoroutineScope for cold-start work`
- **Approach:** Hoisted `CoroutineScope(SupervisorJob() + Dispatchers.IO)` to an `Application` field (`applicationScope`) with a KDoc flagging its intended reuse by future cold-start initializers (e.g. #151 Sentry.init off Main in wave 2). `warmDomainDatabase()` reuses the field.
- **Files changed (1 modified):** GameDealsApplication.kt (+12 / -2).
- **Validation:** `:app:compileDebugKotlin` BUILD SUCCESSFUL with JAVA_HOME=JBR21. `:app` has no `src/test` unit-test source set; no tests to update.
- **Notes:** Existing KDoc on `warmDomainDatabase()` left as-is (still accurate of `applicationScope` runtime contract). Sets up #151 to land cleanly in wave 2.

## Conflicts deferred
None within wave 1. **#151 (Sentry.init off Main) → wave 2** because it would conflict with #153 on `GameDealsApplication.kt` AND reuses the application-scoped CoroutineScope #153 introduces. **#152 (httpClient Swift overload) → wave 2** — independent, but lost the wave-1 slot to the wave-cap-of-4. **#148 (sealed Destination Swift exhaustiveness)** was relabeled out of the campaign (now `tech-debt`/`area:kmp`).

## Sanity-check results
All 4 PRs (#160, #161, #162, #163): branch on origin (✓), 1 commit ahead of dev (✓), OPEN against dev (✓).
