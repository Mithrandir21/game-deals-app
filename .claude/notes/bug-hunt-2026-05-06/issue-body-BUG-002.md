| Field | Value |
|---|---|
| Severity | Medium |
| Category | Startup main-thread blocking |
| Location | `domain/src/commonMain/.../domain/di/DomainModule.kt:27-40`, `domain/src/androidMain/.../domain/di/DomainAndroidModule.kt:13-23`, triggered from `feature/home/.../HomeScreen.kt:86` |
| Effort | Small |
| Confidence | Medium |

**Description.** `DomainDatabase` is a Koin `single` whose factory calls `.build()` on a Room `RoomDatabase.Builder`. Each DAO `single` calls `get<DomainDatabase>().getXxxDao()` — Koin resolves these lazily on first request. The first DAO request happens at `HomeScreen.kt:86` via `viewModel: HomeViewModel = koinViewModel()`, which runs on the **main thread** during composition. `HomeViewModel`'s constructor pulls all five repositories (HomeViewModel.kt:51-58), so Koin transitively resolves `DomainDatabase`. `Room.databaseBuilder(...).build()` performs synchronous file I/O (opens / creates the SQLite file, runs migrations, applies type converters) on the calling thread.

**Impact.** Cold start on a fresh install creates the SQLite file on Main — typically tens of ms. App update with schema bump (current DB version is `4`, with `fallbackToDestructiveMigration(dropAllTables = true)`): destructive migration drops and recreates every table synchronously on Main during the first frame. ANR-class on low-end devices with large databases. Path: `MainActivity.onCreate` → `setContent { NavGraph() }` → `HomeScreen` recomposes → `koinViewModel()` resolves → `DomainDatabase.build()` → SQLite open on Main.

**Evidence.**
```kotlin
// DomainModule.kt
single<DomainDatabase> {
    get<RoomDatabase.Builder<DomainDatabase>>()
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addTypeConverter(get<StoreImagesConverter>())
        ...
        .build()  // synchronous: opens/creates SQLite on caller thread
}
single { get<DomainDatabase>().getDealsDao() }  // first DAO get triggers .build()
```

**Recommended fix.** Move the database open off Main. Cheapest path: in `GameDealsApplication.onCreate`, after `startKoin { ... }`, kick off `CoroutineScope(Dispatchers.IO).launch { val db: DomainDatabase = getKoin().get(); db.openHelper.writableDatabase }` so the SQLite open happens on a background thread before the first frame composes. Cleaner alternatives: `androidx.startup` background `Initializer`, or a suspend `databaseProvider` that opens inside `withContext(Dispatchers.IO)`.

**Confidence rationale.** Medium because the *direction* is unambiguous (Koin singles resolve on the requesting thread; Compose composition runs on Main; Room `.build()` is documented synchronous), but *magnitude* is device- and DB-state-dependent. StrictMode (currently `detectAll().penaltyLog()` per L-2026-05-01-08) should already log this as `StrictModeDiskReadViolation` on cold start in debug — confirm size before fixing.
