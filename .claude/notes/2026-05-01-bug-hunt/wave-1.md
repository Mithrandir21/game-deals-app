# Wave 1 — 2026-05-01

## Summary

- Issues attempted: 4
- PRs opened: 4
- Failures: 0

## Issues

### #31 — `DealsMediator` re-throws `CancellationException` ✓
- PR: [#60](https://github.com/Mithrandir21/game-deals-android-app/pull/60) (open)
- Branch: `wave/2026-05-01-bug-hunt/issue-31-dealsmediator-cancellation`
- Diff: +34 / -0, 1 commit
- Fix: added `import kotlinx.coroutines.CancellationException` and a dedicated `catch (e: CancellationException) { throw e }` block ahead of the generic `Exception` handler. New test asserts the same instance propagates and that `domainDatabase.withTransaction` / `logger.fatalThrowable` are never invoked.
- Tests: `:domain:test` BUILD SUCCESSFUL; one new test added.
- Scope: stayed inside cleared set.

### #41 — `GameDealsTheme` uses `LocalActivity` ✓
- PR: [#61](https://github.com/Mithrandir21/game-deals-android-app/pull/61) (open)
- Branch: `wave/2026-05-01-bug-hunt/issue-41-theme-localactivity`
- Diff: +8 / -4, 1 commit
- Fix: replaced `(view.context as Activity)` with `LocalActivity.current` + null-check; preserved the #44 `LaunchedEffect(colorScheme.primary, darkTheme)` block; dropped the unused `android.app.Activity` import.
- Tests: `:common:ui` has no JVM unit tests (NO-SOURCE); verified via `compileDebugKotlin` / `compileReleaseKotlin`. Robolectric preview-host test was considered but rejected as out of scope (would be the first JVM test in the module).
- **Scope expansion:** added `implementation(libs.androidx.compose.activity)` to `common/ui/build.gradle.kts` so `androidx.activity.compose.LocalActivity` resolves. `activity-compose = 1.11.0` is already in the catalog. Agent flagged transparently.

### #42 — `Storage` interface suspending; prefs I/O off main thread ✓
- PR: [#63](https://github.com/Mithrandir21/game-deals-android-app/pull/63) (open)
- Branch: `wave/2026-05-01-bug-hunt/issue-42-storage-suspend`
- Diff: +188 / -19, 1 commit
- Fix: all 5 `Storage` methods now `suspend`; `SettingStorage` wraps ops in `withContext(ioDispatcher)` with injected `CoroutineDispatcher` (default `Dispatchers.IO`); kept `.commit()` since now off main thread. Added StrictMode `detectAll().penaltyLog()` in `GameDealsApplication.onCreate` gated on `ApplicationInfo.FLAG_DEBUGGABLE`.
- Tests: `:common:test :app:assembleDebug` BUILD SUCCESSFUL; 8 new tests, 0 failures.
- **Scope expansions (flagged by agent):**
  - Added `mockk`, `coroutines.testing` testImplementations and `coroutines` implementation to `common/build.gradle.kts`. Precedented (issue #45 did the same).
  - StrictMode gate uses `ApplicationInfo.FLAG_DEBUGGABLE` instead of `BuildConfig.DEBUG`. Reason: AGP 8 doesn't auto-generate `BuildConfig` for the app module without `buildFeatures.buildConfig = true`; using the manifest debuggable flag avoids opting in to BuildConfig generation just for one boolean. Equivalent behavior — debug build type implicitly sets `debuggable=true`.
- Confirmed via grep before changing the interface: zero production callers of `Storage` (only DI binding in `CommonModule.kt`).

### #35 — `LazyColumn.items()` stable keys ✓
- PR: [#62](https://github.com/Mithrandir21/game-deals-android-app/pull/62) (open)
- Branch: `wave/2026-05-01-bug-hunt/issue-35-lazycolumn-keys`
- Diff: +38 / -20, 1 commit
- Fix: added `key` parameters to all four flagged `items(count)` calls. Used Room `@PrimaryKey` fields (`Release.title`, `Giveaway.id`); for the mixed `data.items` sealed list a composite prefix is required because `StoreData` and `ViewAllData` share `storeID`.
- Tests: `:feature:home:test :feature:giveaways:test` BUILD SUCCESSFUL.
- **Scope (within cleared set, flagged):** the `ScreenPreview` sample data inside `HomeScreen.kt` had duplicate identifiers (two `"Game 1"` releases, all giveaways sharing `id=123`, twelve `data.items` reusing `storeID=1`). With keys, the preview would crash on the duplicate-key check. Fixed by giving each preview entry a unique id/title/dealID.

## Sanity check

| PR | state | base | additions | deletions | commits |
|---|---|---|---|---|---|
| #60 | OPEN | dev | 34 | 0 | 1 |
| #61 | OPEN | dev | 8 | 4 | 1 |
| #62 | OPEN | dev | 38 | 20 | 1 |
| #63 | OPEN | dev | 188 | 19 | 1 |

All four pass.

## Notes

- Three of four agents reported the same operational quirk: the worktree was missing `local.properties` and/or had `gradlew` non-executable; `bash gradlew` + a copied `local.properties` worked around it without touching project files. Worth watching — if this recurs across more campaigns, the orchestrator could pre-seed worktrees.
- Two scope expansions touched build.gradle.kts files (#41 and #42). Both were unavoidable (compile-time dependency for the chosen approach) and both agents flagged transparently — desired behavior.
