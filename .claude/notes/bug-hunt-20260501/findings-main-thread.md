# Bug Hunt 2026-05-01 — Main-Thread Violations

## Summary

Counts by severity:

| Severity | Count |
|----------|-------|
| Critical | 0 |
| High     | 0 |
| Medium   | 0 |
| Low      | 0 |

**Top 3 titles:** N/A — zero defects found.

## Findings

No main-thread (UI thread) violations were found in production `*/src/main/**/*.kt` sources (excluding `.claude/worktrees/**` and `build/**`).

## Cue-by-cue verification

**Room DAOs.** All 6 DAOs (`DealsDao`, `GamesDao`, `GiveawaysDao`, `PagingDao`, `ReleasesDao`, `StoresDao`) expose only `suspend` methods or `Flow`/`PagingSource` returns. `Room.databaseBuilder` at `/Users/bam/REPO/PRIVATE/game-deals-android-app/domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt:50-58` does **not** call `allowMainThreadQueries()`.

**Retrofit calls.** All endpoints in the 5 API interfaces (`DealsApi`, `GamesApi`, `ReleaseApi`, `StoresApi`, `gamerpower/GamesApi`) are declared `suspend` and return `ApiResponse<...>`. No `Call<T>` types and no `.execute()` anywhere in production sources.

**SharedPreferences / File I/O.** The only `SharedPreferences` consumer is `/Users/bam/REPO/PRIVATE/game-deals-android-app/common/src/main/java/pm/bam/gamedeals/common/storage/SettingStorage.kt`. Every read/write/contains/remove is wrapped in `withContext(ioDispatcher)` (default `Dispatchers.IO`). The `.commit()` calls at lines 40 and 48 are intentional and documented — they are already off-main and callers rely on the boolean result. There are no `FileInputStream`/`FileOutputStream`/`FileReader`/`BufferedReader`/`openFileInput`/`openFileOutput`/`Files.readAll*`/`readText()` usages in production sources.

**JSON parsing on main.** `SerializerImpl` (`kotlinx.serialization.json.Json` wrapper) is only invoked from (a) `SettingStorage` inside `withContext(ioDispatcher)`, (b) Room `TypeConverter`s (`StoreImagesConverter` at `/Users/bam/REPO/PRIVATE/game-deals-android-app/domain/src/main/java/pm/bam/gamedeals/domain/utils/TypeAdapters.kt:18-23`) which run on Room's query executor (suspend/Flow DAOs only), and (c) the Retrofit Kotlin-serialization converter on OkHttp's dispatcher.

**ViewModel `init {}` blocks.** None do synchronous heavy work; all launch coroutines via `viewModelScope.launch { ... }`:
- `feature/deal/.../DealDetailsViewModel.kt`: no init.
- `feature/game/.../GameViewModel.kt:49-59`.
- `feature/giveaways/.../GiveawaysViewModel.kt:36-44`.
- `feature/home/.../HomeViewModel.kt:75-77` (calls `loadTopStoresDeals()` which launches).
- `feature/search/.../SearchViewModel.kt:42-63`.
- `feature/store/.../StoreViewModel.kt:47-57`.

**`Application.onCreate`.** `/Users/bam/REPO/PRIVATE/game-deals-android-app/app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt:17-28` only enables `StrictMode` in debug. Coil `ImageLoader` is constructed lazily by Hilt.

**`Activity.onCreate`.** `MainActivity.onCreate` only calls `setContent { NavGraph() }`. `LoggingBaseActivity` lifecycle hooks only emit through the injected `Logger`, which fans out to in-memory `LoggingInterface` listeners — no I/O.

**Compose composables.** Verified via Grep: no `runBlocking`, `GlobalScope`, `MainScope`, `Looper.getMainLooper`, `Thread.sleep`, `.blockingGet`/`.blockingFirst`, `Class.forName`, or `System.load*` in production sources. No heavy work inside `derivedStateOf`/`produceState` (zero matches). No `remember { json|parse|decode|read|sort|filter ... }` patterns (zero matches). The only main-thread WebView usage in `feature/webview/.../WebView.kt` is via `AndroidView { factory = { ... } }` and `onRelease`, which is the documented API for `android.webkit.WebView` — not a violation.