# Campaign lessons — 2026-05-15-bug-hunt-severity-low

## Campaign lessons

- (wave 1) Issue #149 — **`Eagerly` → `WhileSubscribed(5_000)` is not test-transparent.** Tests authored against `Eagerly`'s primed-upstream semantics often break in two ways: (a) `assertEquals(1, emissions.size)` with `emissions.first()` becomes wrong because `WhileSubscribed` emits the placeholder initialValue *before* the upstream's first real emission — switch to `emissions.last()` and drop `size == 1`; (b) tests that call an imperative trigger (`viewModel.reloadGiveaways()`) *before* subscribing observe nothing because there's no subscriber yet — reorder to subscribe-first, and gate the underlying suspend call with `CompletableDeferred` so the trigger's intermediate states (LOADING) aren't masked by a fast-returning stub. 5 of 11 existing tests needed adjustment when this swap landed.

- (wave 1) Issue #147 — **Room KSP accepts `@Transaction suspend fun` default methods on `interface` DAOs in Room 2.8.x for both Android AND iOS** (KMP). The `@Transaction` default-method pattern with `EXISTS`-style sub-queries is the cleanest way to land atomic read-modify-write on a Room table, and it keeps the repo's public signature unchanged. Mokkery stubs the new suspend method straightforwardly via `everySuspend { dao.toggleFavourite(...) } returns <bool>` — no unusual configuration.

- (wave 1) Issue #147 — **`Pair<A, B>` in Compose parameter types is unstable regardless of generic args.** Wrapping in `ImmutableList<Pair<A, B>>` doesn't help — the element type still makes the parameter unstable for skippability. Fix: introduce a small named `@Immutable data class FooBarPair(val foo: A, val bar: B)` per pair shape. Bonus: `.first/.second` becomes `.foo/.bar` at consumer sites, which is more readable.

- (wave 1) Issue #150 — **DAO methods don't have access to `Clock`** — when pushing logic from the repo into a DAO via `@Transaction`, factor any `dateAddedMs: Long` (or similar wall-clock parameter) into the DAO method's signature and let the repo pass `clock.nowMillis()`. Keeps the deterministic-clock contract for tests intact without polluting the DAO with a `Clock` dependency.

## Promotion candidates (project-wide)

- [x] (promoted 2026-05-15) **`SharingStarted.Eagerly` → `WhileSubscribed(5_000)` swap on a VM's `uiState` is not test-transparent.** Expect to update 2–5 tests per VM: (a) replace `emissions.first()` / `size == 1` with `emissions.last()`; (b) reorder subscribe-first when a test drives an imperative trigger; (c) gate any underlying suspend stub with `CompletableDeferred` so intermediate states aren't masked. — drafted from issue #149 / PR #162.

- [x] (promoted 2026-05-15) **`Pair<A, B>` is unstable in Compose; the `ImmutableList` wrapping doesn't rescue it.** When you see a composable parameter typed `ImmutableList<Pair<A, B>>` (or worse, `List<Pair<A, B>>`), introduce a named `@Immutable data class FooBarPair(val foo: A, val bar: B)`. Refines `L-2026-05-02-02` ("@Immutable + ImmutableList on every domain model used as a composable parameter") to cover the element-type case. — drafted from issue #147 / PR #163.

- [x] (promoted 2026-05-15) **Room `@Transaction suspend fun` default methods on `interface` DAOs work cleanly on Room 2.8.x KMP — both Android and iOS KSP accept them.** Useful pattern for atomic read-modify-write without touching the repo's public signature. — drafted from issue #150 / PR #161.
