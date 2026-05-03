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

(populated as work proceeds)

## Build verification

(populated as work proceeds)
