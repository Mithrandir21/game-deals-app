# Campaign lessons — 2026-05-06-bug-hunt-severity-medium

## Campaign lessons

- **(wave 1) Issue #123 — `:app` cannot access Room internals:** The issue body's recommended fix (call `db.openHelper.writableDatabase` to force the file open) was unreachable from `GameDealsApplication` because `:app` only `implementation`s `:domain`, and `:domain` declares Room as `implementation` (not `api`). So `RoomDatabase`'s superclass methods aren't on `:app`'s compile classpath. The worker recognized this and took the smaller path. Process tip: when writing a bug-hunt "Recommended fix" that names internal types of a port-module, sanity-check the consumer's `build.gradle.kts` first — recommend a path that's actually compilable in the cited module.

- **(wave 1) Issue #123 — Room `.build()` doesn't actually open the SQLite file:** The issue body claimed `Room.databaseBuilder(...).build()` "performs synchronous file I/O (opens / creates the SQLite file, runs migrations, applies type converters)". That's only partly accurate — `.build()` does in-memory wiring + initial schema validation, but the actual SQLite file open and any destructive migration run on the **first query**, not on `.build()`. The worker's compromise (just resolve the singleton, which forces `.build()`) happens to be exactly the right surface to move off-main, since first DAO access in this codebase is always via a coroutine-dispatcher path.

- **(wave 1) Issue #124 — Mokkery, not MockK:** This codebase uses Mokkery (KMP-compatible) in `commonTest` source sets, NOT MockK (JVM-only). The orchestrator prompt and issue body referenced MockK based on stale `docs/patterns/testing.md` (surveyed 2026-05-03 at `31a89bc`, before KMP migration completed). The worker noticed and adapted. **See promotion candidate below.**

## Promotion candidates (project-wide)

- [x] **Test mocking library is Mokkery, not MockK** — promoted as **L-2026-05-06-04** in `.claude/lessons.md` on 2026-05-06.
  - Why: MockK ships JVM-only; KMP `commonTest` source sets need a multiplatform-compatible mocker. The project switched to Mokkery during the KMP migration. `docs/patterns/testing.md` is stale on this point.
  - How to apply: when writing or modifying tests in any `commonTest` source set, use Mokkery's `every` / `everySuspend` / `returns` / `throws` / matchers — not MockK's `coEvery` / `coVerify`. JVM-only tests outside KMP modules (none currently exist in this project) could theoretically still use MockK, but default to Mokkery for consistency. Look at existing `StoreViewModelTest` / `GiveawaysViewModelTest` for the canonical patterns.

  Tentative full body if you choose to promote:
  ```
  L-2026-05-06-04 · Test mocking library is Mokkery, not MockK
  Status: active · Confidence: confirmed · Tags: testing, mokkery, kmp, commontest
  Applies to: any test in *commonTest source set, and by convention all tests in this codebase

  This codebase uses Mokkery (`dev.mokkery:mokkery`) for mocking, not MockK.
  Mokkery is KMP-compatible; MockK ships JVM-only and cannot be used in
  commonTest. Use `every { … } returns …` for non-suspend, `everySuspend { … }
  returns …` for suspend, `throws` for exception stubs, and matchers like
  `any()`, `eq(...)`. The conventions are visible in feature/*/src/commonTest
  ViewModel tests (StoreViewModelTest, GiveawaysViewModelTest, etc.).

  Note: docs/patterns/testing.md still says "MockK Everywhere" — that doc was
  surveyed 2026-05-03 at SHA 31a89bc, before the KMP migration completed. The
  pattern is stale; this lesson takes precedence until the patterns doc is
  refreshed.

  Source: 2026-05-06-bug-hunt-severity-medium wave 1 (PR #132 / issue #124).
  ```
