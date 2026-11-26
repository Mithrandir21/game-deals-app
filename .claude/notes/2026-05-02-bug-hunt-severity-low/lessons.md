# Campaign lessons — 2026-05-02-bug-hunt-severity-low

## Campaign lessons

- **(wave 1) Issue #78 — converting `_uiState.value.copy(...)` to `update {}`:** distinguish field-level merges from full-state replacements. Only the LOADING/ERROR transitions in HomeViewModel and GiveawaysViewModel were RMW-on-current-state; the success-path `.collect { _uiState.emit(it) }` calls emit a fresh full state derived from upstream — no race, leave them. When the LOADING transition moves out of a flow chain into a side-effect, the chain may need an explicit `flow<Unit>` type pin to keep `logFlow` inference happy.

- **(wave 1) Issue #79 — `@Immutable` + `ImmutableList` on a `@Serializable` model:** kotlinx-serialization 1.9.0 (the catalog version) supports `ImmutableList` natively. No custom serializer required, no impact on `Saver` round-trips that go through `Json.encodeToString`. Pattern: just retype, add `@Immutable`, replace `.toMutableList().map { … }` with `.map { … }.toImmutableList()`.

- **(wave 1) `domain/build.gradle.kts` is now a serial bottleneck for ImmutableList migrations:** any two issues that both add `implementation(libs.kotlinx.collections.immutable)` to `:domain` will conflict. Schedule them in separate waves, or fold them into one issue if the catalog is hot.

- **(wave 2) Issue #80 — when a `:domain` type adopts an `ImmutableList` field, every consuming module that imports an extension function on it (e.g. `kotlinx.collections.immutable.toImmutableList`) needs `implementation(libs.kotlinx.collections.immutable)` too.** Gradle `implementation` is non-transitive on the compile classpath. The cheapshark mapper failed to compile until `:remote:cheapshark` got the dep added explicitly. Pre-wave planning didn't catch this because the planner agent only inspected the type usage in `GameMappers.kt`, not the import-graph implications. **Heuristic for future planners:** any time an issue retypes a `:domain` field to use a third-party collection/type, walk the consumer modules and pre-add the dep to every `build.gradle.kts` that touches the type.

## Promotion candidates (project-wide)

- [x] **`MutableStateFlow.update { it.copy(...) }` is the correct primitive for field-level merges.** — Promoted to `.claude/lessons.md` as L-2026-05-02-01.

- [x] **For Compose stability: `@Immutable` + `ImmutableList<…>` on every domain model used as a composable parameter.** — Promoted to `.claude/lessons.md` as L-2026-05-02-02.

- [x] **Gradle `implementation` is non-transitive on the compile classpath: when a domain type's field type is replaced (e.g. `List<T>` → `ImmutableList<T>`), every consumer module that uses an extension function on that type needs the same dep added explicitly.** Symptom: clean unit-test runs in `:domain` itself, but `:remote:*` (or `:feature:*`) fails to compile because the import (`kotlinx.collections.immutable.toImmutableList`) doesn't resolve. The fact that `:domain` re-exports the *type* (because it's a public-API type in a public function signature) is misleading — `implementation` doesn't re-export *extensions* on it. — drafted from issue #80 (PR #83). **Promoted to `.claude/lessons.md` as L-2026-05-02-03.**
