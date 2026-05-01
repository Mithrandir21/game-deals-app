# Lessons Learned

Condensed, structured lessons from past development sessions. Claude reads this file at the start of each session (via a separate skill) so it can apply past learnings without re-deriving them.

Each lesson has an immutable ID. When a lesson is superseded or turns out to be wrong, it is moved to `## Archive` with an updated `Status` line — its content is never rewritten. This preserves the audit trail.

**Claude:** apply lessons from `## Active` only. Consult `## Archive` only if something appears contradictory and you need the history.

## Active

### L-2026-04-30-06 · `_uiState.stateIn(WhileSubscribed, initial)` is the wrong shape — use `_uiState.asStateFlow()`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** viewmodel, stateflow, coroutines, compose
**Applies to:** New screen ViewModels in `feature/*` modules; copying the existing convention from Home/Store/Giveaways/Search/Game/DealDetails ViewModels.

The current convention `private val _uiState = MutableStateFlow(initial); val uiState = _uiState.stateIn(viewModelScope, WhileSubscribed(5000), initial)` is broken on two counts. (1) `MutableStateFlow` is already a hot, conflated, replay-1 StateFlow — `stateIn` produces a *second* derived StateFlow whose state machine is independent of `_uiState`. (2) Under `WhileSubscribed(5000)`, after subscribers drop, `uiState.value` returns the frozen last derived value, and new subscribers see `initialValue` for one frame even when `_uiState` has a different value — producing a spurious "LOADING" flash on resume after >5 s of backgrounding. Use `_uiState.asStateFlow()`. Tracked as issue #37 across all six existing ViewModels.

**Source:** android-bug-hunting-dispatcher audit (issues #30–#48)

### L-2026-04-30-05 · `flow { emitAll(repo.observeXxx()) }` is intentional — preserves synchronous-throw safety
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** kotlin, coroutines, flow, repository, error-handling
**Applies to:** ViewModel code that consumes a repository `observeXxx()` Flow returned by `:domain` repositories

When you see `flow { emitAll(repo.observeXxx()) }.map{…}.catch{…}`, do not "simplify" the wrapper away. The repository's `observeXxx()` returns `Flow<…>` but can throw synchronously during construction (e.g., backing-store read failures surfaced before the first emission). Wrapping in `flow {}` converts that synchronous throw into an upstream exception that downstream `.catch {}` can handle; without the wrapper the throw escapes the `viewModelScope.launch` builder. Examples in `HomeViewModel.loadTopStoreDataFlow` / `loadNewReleases` / `loadGiveaways` and `GiveawaysViewModel.{init, loadGiveaway}`.

**Source:** PR refactor/18-24-screen-state revert

### L-2026-04-30-04 · Keep ViewModel functions Flow-shaped; don't lower to `viewModelScope.launch { try/catch }`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** kotlin, coroutines, flow, viewmodel, architecture
**Applies to:** Any feature ViewModel function in this project that triggers work in response to a UI event

In this project, ViewModel handlers are deliberately structured as `viewModelScope.launch { someFlow.onStart{…}.map{…}.onError{…}.onCompletion{…}.catch{…}.collect { _state.emit(it) } }`. Do not "modernize" them into imperative `try/catch` blocks even when the body is short — Flow is the medium that composes loading-state emission, `logFlow`, `mapDelayAtLeast` (minimum-loading UX), `onError`/`onCompletion` rethrow semantics, and SharedFlow event side-effects. A bulk lowering across `feature/*` was attempted (b41c34d) and reverted (4f20fa5). Generalizes L-2026-04-27-01 from "which error helper" to "what shape is the function."

**Source:** PR refactor/18-24-screen-state revert

### L-2026-04-30-03 · Test internals from inside the owning module, not via test-only factories
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** testing, multi-module, mockwebserver, internal-visibility
**Applies to:** Multi-module Android projects where a facade/data-source has `internal` collaborators (Retrofit `*Api` types, KSP-generated artifacts) that an outside test wants to construct against `MockWebServer`.

Do not introduce a test-only `Factory.create(...)` in `main/` to give a downstream module's test access to `internal` collaborators — the cost is permanent (a production-source seam that exists only for tests) and the test pattern stops matching its peers. Keep downstream repository tests mocking the facade interface like their peers, and put the HTTP-wiring/integration test inside the module that owns the impl: same module's `test/` source set sees `internal` for free. Net effect: repository tests stay narrow at the facade boundary, integration coverage lives at the right layer, and no test-only types leak into production source.

**Source:** PR #27 (refactor/15-remote-source-facade)

### L-2026-04-30-02 · GitHub Actions JDK must match `compileOptions.targetCompatibility`, not just the Kotlin toolchain
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** ci, gradle, hilt, jdk, github-actions
**Applies to:** Android projects with Hilt where the workflow JDK is older than the project's `compileOptions.targetCompatibility`

When the project's `compileOptions.targetCompatibility` (and matching Kotlin `jvmToolchain(...)`) is newer than the runner's JDK, most Java compile tasks succeed because Gradle auto-provisions the toolchain. But `:app:hiltJavaCompileDebug` runs as a plain `JavaCompile` against Gradle's own JVM — *not* the toolchain — and fails with `error: invalid source release: <N>`. Set the workflow JDK (`actions/setup-java@v4` `java-version`) to at least the project's target version; don't rely on the Kotlin toolchain to paper over the mismatch.

**Source:** Wave 1 PR #25 (Hilt KAPT → KSP)

### L-2026-04-30-01 · Ports in `:domain`, adapters in `:remote:*`, wiring in `:app`
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-30 · **Tags:** hilt, di, multi-module, ports-and-adapters, architecture
**Applies to:** Any refactor that lifts an interface from a remote/data module up into `:domain` so `:domain` no longer depends on `:remote:*`

When you move a Source/Repository *interface* up to `:domain` (the port) but leave its `@Provides`-bound impl down in `:remote:*` (the adapter), `:domain` correctly drops `implementation(project(":remote:*"))`. But the composition root — `:app` — must then add those `:remote:*` modules as `implementation` deps itself, otherwise Hilt fails at `:app:hiltJavaCompileDebug` with `MissingBinding` for the port type. The Hilt graph follows Gradle visibility; if `:app` can't see the adapter module, it can't see its `@Module @Provides`. This is mechanical, not a design flaw — but it surprises you the first time because the symptom shows up at app-level Hilt compilation rather than at the module boundary.

**Source:** Wave 2 PR #29 (issue #17 — Remote→Domain mapper relocation)

### L-2026-04-27-01 · Prefer flow-native error helpers over `runCatching` inside Flow chains
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-27 · **Tags:** kotlin, coroutines, flow, error-handling
**Applies to:** ViewModel/repository code that wraps a single suspend call inside a Flow pipeline

When lifting a `suspend` call into a Flow, prefer `flow { emit(call()) }` plus the project's `catchAndContinue(defaultValue, action)` helper from `common/logic/util/FlowExtensions.kt` over `runCatching { ... }.getOrNull()`. It keeps error handling on the Flow itself (so cancellation and downstream operators see the right thing) and reuses the codebase's existing helper instead of inlining a raw `.catch { ... emit(default) }`.

**Source:** CommitmentPackagesViewModel FAQ refactor

### L-2026-04-20-01 · On migration branches, map conflicting *features* not *files*
**Status:** active · **Confidence:** confirmed · **Added:** 2026-04-20 · **Tags:** merge-conflicts, migration, di, architecture
**Applies to:** Any long-running branch refactoring infrastructure (DI, networking, shared modules) while `main` ships features

Don't resolve merge conflicts file-by-file. First identify which *features* landed on `main` during the migration, then decide per feature: was it already migrated? Does it need to be? Does it stay where it is? This gives a coherent strategy instead of ad-hoc ours/theirs picks that silently break DI wiring. In practice, a dozen conflicting files often reduce to just two or three feature-level decisions.

**Source:** merge conflict resolution

## Archive
