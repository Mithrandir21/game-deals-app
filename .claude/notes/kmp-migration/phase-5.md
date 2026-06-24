# Phase 5 — `:domain` → KMP + feature modules → KMP

**Branch:** `feature/kmp-migration-phase-5-features`
**Started:** 2026-05-03
**Tag at start:** `kmp-pre-phase-5` → `ccdb6c5`

## Sub-phase plan

Phase 5 in PLAN.md is one branch but ~4–5 weeks of work. Splitting:

### 5.1 — `:domain` to KMP (this PR)

Prerequisite for everything else. Three commits:

- **5.1a** — Apply `pm.bam.gamedeals.kmp.library` + `kmp.ksp` + `kotlinx.serialization` conventions to `:domain`. Move `src/main/java` → `src/androidMain/kotlin` and `src/test/java` → `src/androidUnitTest/kotlin`. Zero content change. Verify `:app:assembleDebug` + `:domain:test` green. Safe checkpoint — only build wiring changed.
- **5.1b** — Swap `androidx-paging` (`paging-runtime-ktx`, Android-only) → `androidx-paging-common` (multiplatform). `RemoteMediator`, `PagingState`, `LoadType`, `Pager`, `PagingData`, `PagingConfig` all live in `paging-common`. `paging-compose` (used by feature modules' `LazyPagingItems`) is unaffected — feature modules still consume it through their own deps.
- **5.1c** — Room → Room KMP. Apply `androidx.room` Gradle plugin. Wire `libs.room.runtime.multiplatform` in commonMain. Move entities/DAOs/database to commonMain. `Room.databaseBuilder<T>(name)` KMP API + `expect/actual` driver factory in `:domain` (Android: bundled SQLite driver via `BundledSQLiteDriver` or AndroidSQLiteDriver). DI module updated. `androidx.room.withTransaction` extension (room-ktx, Android) → KMP equivalent. `@Immutable` annotation on entities — depends on Compose runtime; either drop or move to commonMain via CMP.

### 5.2 → 5.8 — features in order (subsequent PRs)

home → search → game → store → giveaways → deal → webview. One feature per PR. Each: sources to commonMain, `R.string`/`R.drawable` → `compose.resources`, lifecycle/navigation multiplatform, Coil 2 → Coil 3, WebView gets `expect/actual`.

## Sub-commits

### 5.1a — `:domain` to KMP convention (commit `0e035d3`)

Build wiring only — `pm.bam.gamedeals.kmp.library` + `kmp.ksp`. Sources moved
`src/main/java` → `src/androidMain/kotlin`, tests → `src/androidUnitTest/kotlin`,
manifest → `src/androidMain/AndroidManifest.xml`. Dropped the
`gamedeals.android.library.compose` convention from `:domain` — `:domain` only
used Compose for the `@Immutable` marker annotation, so the Compose compiler
plugin was unnecessary. iOS targets compile to empty klibs trivially.

### 5.1b — Paging swap to multiplatform (commit `e3d886e`)

`libs.androidx.paging` (`paging-runtime-ktx`, Android-only) →
`libs.androidx.paging.common`. All `:domain` paging usages (`RemoteMediator`,
`PagingState`, `LoadType`, `Pager`, `PagingData`, `PagingConfig`,
`PagingSource`) live in `paging-common`. `paging-runtime-ktx`'s extras
(`LivePagedListBuilder`, `PagingDataAdapter`, RxJava bridges) are unused.

### 5.1c — Room → Room KMP (this commit)

Applied the `androidx.room` Gradle plugin to `:domain` and added a new
`androidx-room` plugin alias to `gradle/libs.versions.toml`. Wired
`libs.room.runtime.multiplatform` (= `androidx.room:room-runtime`) in
commonMain; kept `libs.room.runtime` (= `room-ktx`) and `libs.room.paging`
in androidMain because `withTransaction` and the room-paging integration
are still Android-only. `room { schemaDirectory("$projectDir/schemas") }`
configured (Room plugin requires it).

Moved to commonMain:
- `db/DomainDatabase.kt` + all `db/dao/*.kt`
- `models/*.kt` (entities + UI models)
- `utils/{Constants,TypeAdapters,TypeSerializers,ImmutableListSerializer}.kt`

Stayed in androidMain (use Android/JVM-only APIs):
- `di/DomainModule.kt` — `androidContext()`, `Executors.newSingleThreadExecutor()`,
  Android-style `Room.databaseBuilder(context, klass, name)`.
- `repositories/**` — use `androidx.room.withTransaction` (room-ktx, Android).
- `source/**` — interfaces stay next to their consumers.

`@Immutable` annotations dropped from all entities + UI models. Compose-
stability inference handles data classes with stable field types fine; the
`@Immutable` hint is optimisation-only, not load-bearing semantics. Worth
revisiting once features actually move to commonMain and recomposition
profiling tells us whether explicit hints are needed; `org.jetbrains.compose.runtime.Immutable`
becomes available on commonMain via CMP at that point.

`@ConstructedBy(DomainDatabaseConstructor::class)` added to the database;
`expect object DomainDatabaseConstructor : RoomDatabaseConstructor<DomainDatabase>`
declared with `@Suppress("NO_ACTUAL_FOR_EXPECT")` — Room's KSP processor
generates the per-target `actual`s. Only `kspAndroid` is wired (no iOS-side
KSP); the iOS klib type-checks the abstract declaration but no DB instantiation
exists for iOS yet. Phase 6 will wire iOS-side KSP + `BundledSQLiteDriver`.

Also dropped `androidx-ktx`, `appcompat`, `material`, and
`androidx-compose-runtime` (= `lifecycle-runtime-compose`) deps from `:domain` —
all unused after the `@Immutable` removal.

### 5.1d — Fix paged-deals fixture termination (test-only)

`HomeToStoreToDealJourneyTest` started failing with `ComposeNotIdleException` at
the `StoreTopBar` waitUntil. Logcat showed `DealsMediator` was firing
`Loading: APPEND` on a tight loop, reaching page ~248 within the first 6 seconds
of test runtime — Compose couldn't reach idle.

Root cause: latent fixture bug. `deals_storeid_1_paged.json` returns the same
two deals (Portal 2 + Half-Life 2) on every paged request. `DealsMediator.load`
returns `Success(endOfPaginationReached = deals.isEmpty())` — never trips since
the mock is non-empty. The fixture has been broken since it was added; Phase 4
tidy passed by timing luck (Compose found idle moments between paging
emissions), Phase 5's downstream timing changes (paging-common-only resolution
in `:domain` + entity stability after `@Immutable` removal) closed those
moments.

`git diff kmp-pre-phase-5..HEAD -- app/src/androidTest/` was empty before this
commit — production code was untouched, fix is purely in the fixture handler.

`FixtureRequestHandler.handle` now returns `[]` for paged Cheapshark deals
where `pageNumber != "0"` (modelling real API end-of-pagination). Page 0 still
serves the populated fixture so the test finds `DealRowabc123` and proceeds.

### 5.1 build + connected-test verification

| Task | Result |
|---|---|
| `:domain:compileDebugKotlinAndroid` | ✅ |
| `:domain:compileKotlinIosSimulatorArm64` | ✅ (clean rebuild after `:domain:clean`) |
| `:domain:testDebugUnitTest` | ✅ (8 test files, all pass) |
| `:app:assembleDebug` | ✅ |
| `./gradlew test` (whole project) | ✅ |
| `:app:connectedDebugAndroidTest` (HomeToStoreToDealJourney) | ✅ (after 5.1d) |
| All other module connectedAndroidTest | ✅ |
