# Findings — Main-Thread Violations

Hunter: `android-bug-hunting-main-thread-violations`
Date: 2026-05-02
Branch HEAD: `wave/2026-05-02-bug-hunt/issue-75-77-giveaways-refresh-outcome`

## Summary

**No findings.** The codebase is clean on main-thread / blocking-I/O violations at HEAD.

Found 0 findings (0 Critical, 0 High, 0 Medium, 0 Low).

## Detectors Run

| Detector | Result |
| --- | --- |
| D1 — Room DAO non-suspend / non-Flow returns | clean |
| D2 — `Database.allowMainThreadQueries()` in production | clean (only in `app/src/androidTest/java/pm/bam/gamedeals/di/TestDatabaseModule.kt:31`, test-only) |
| D3 — `SharedPreferences.commit()` on Main | clean (`SettingStorage.commit()` runs inside `withContext(ioDispatcher)`) |
| D4 — Network calls on Main | clean (all Retrofit endpoints are `suspend`) |
| D5 — File I/O on Main | clean in production (`app/build.gradle.kts` reads `local.properties` at Gradle config time; `FixtureMockDispatcher` is androidTest-only) |
| D6 — `BitmapFactory.decode*` on Main | clean |
| D7 — JSON parsing on Main | clean (kotlinx.serialization runs inside Retrofit converter or off-main `withContext`) |
| D8 — Heavy work in `onCreate`/`onResume` | clean (`MainActivity.onCreate` only calls `setContent`; `GameDealsApplication.onCreate` only configures StrictMode in debug) |
| D9 — `Thread.sleep` / blocking on Main | clean (only `kotlinx.coroutines.delay` used) |
| D10 — `getSharedPreferences` first-touch on Main | clean (`Storage` API is `suspend` and dispatches to IO) |
| D11 — `ContentResolver.query` on Main | clean (no usage) |
| D12 — `WebView.evaluateJavascript` heavy callbacks | clean (no usage; WebView lifecycle properly handled by fix #67) |
| D13 — `PackageManager` IPC queries on Main | clean (no usage) |
| D14 — Synchronous WorkManager scheduling on Main | clean (no usage) |

## Key Evidence

- StrictMode `detectAll().penaltyLog()` enabled in debuggable builds — `app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt:21-26`
- All Room DAOs under `domain/src/main/java/pm/bam/gamedeals/domain/db/dao/` are `suspend` / `Flow<…>` / `PagingSource`
- `Room.databaseBuilder` at `domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt:50-58` does not call `allowMainThreadQueries()` in production
- `SettingStorage.save()` / `remove()` use `.commit()` deliberately at `common/src/main/java/pm/bam/gamedeals/common/storage/SettingStorage.kt:40,48`, but both call sites are wrapped in `withContext(ioDispatcher)` (lines 32, 47). Retained `commit()` because callers need the accurate `Boolean` return.
- All Retrofit endpoints are `suspend fun` (verified across `remote/cheapshark/.../api/*.kt` and `remote/gamerpower/.../api/GamesApi.kt`)
- ViewModel `init` blocks (`HomeViewModel`, `GiveawaysViewModel`, `SearchViewModel`, `GameViewModel`, `StoreViewModel`) wrap collection in `viewModelScope.launch { … }` so suspend Room/Retrofit calls dispatch off Main

## Considered-Then-Rejected

- `LoggerImpl.log` (`logging/src/main/java/pm/bam/gamedeals/logging/LoggerImpl.kt:18`) invokes `messageProvider()` whenever any `LoggingInterface.isEnabled()` returns `true`. `SimpleLoggingListener.isEnabled()` always returns `true`, so `it.toString()` inside `logFlow` (`common/src/main/java/pm/bam/gamedeals/common/CommonFlowExtensions.kt:23-28`) is evaluated on every flow emission even in release. The `toString()` runs on Main (default of `viewModelScope`), but the screen-state data classes here are bounded (≤ ~100 items). Not a violation by the playbook's bar.
