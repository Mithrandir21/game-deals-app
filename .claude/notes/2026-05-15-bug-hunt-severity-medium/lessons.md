# Campaign lessons — 2026-05-15-bug-hunt-severity-medium

## Campaign lessons

- (wave 1) Issue #146 — `Dispatchers.IO` is internal on Kotlin/Native **everywhere**, not just commonMain. The existing project lesson `L-2026-05-06-03` ("`Dispatchers.IO` is not accessible from `commonMain` in coroutines 1.10.x") can be read to imply it's reachable from `iosMain`. It isn't. First compile in `iosMain` failed with `Cannot access 'val IO: CoroutineDispatcher': it is internal`. The correct iOS substitute is `Dispatchers.Default` (which is what K/N's `IO` aliases to internally anyway). Refinement candidate for `L-2026-05-06-03`'s wording.

- (wave 1) Issue #145 — `rememberUpdatedState` (per `L-2026-05-02-06`) applies to **caller-provided lambdas** captured in `LaunchedEffect`. When a snackbar action handler can call the VM directly (`viewModel.retry()` — VM identity is stable across recomposition), `rememberUpdatedState` is not needed. The pattern matters specifically for hoisted `onX: () -> Unit` parameters whose closures might rotate as the parent recomposes.

- (wave 1) Issue #145 — Pattern for a `retry()` method on a VM with a `flatMapLatest`-shaped upstream: introduce a `private val retryTrigger = MutableStateFlow(0)`, combine into the upstream (`retryTrigger.flatMapLatest { ... }` for full re-subscription, or `combine(otherFlow, retryTrigger) { ... }.flatMapLatest { ... }` for re-trigger of a downstream load), and `retry()` is `retryTrigger.update { it + 1 }`. `flatMapLatest` cancels the in-flight inner flow on each new trigger value, which is the desired behaviour after an error.

## Promotion candidates (project-wide)

- [ ] **Refine `L-2026-05-06-03`**: `Dispatchers.IO` is `internal` in `kotlinx.coroutines.Dispatchers` on Kotlin/Native regardless of source set — applies to `iosMain` and `commonMain` alike, not just commonMain. Use `Dispatchers.Default` from any Kotlin/Native source set. — drafted from issue #146

- [ ] **`rememberUpdatedState` scope clarification**: only needed for caller-provided lambdas captured in `LaunchedEffect`/`DisposableEffect`. Direct VM calls (`viewModel.retry()`) from inside `LaunchedEffect` don't need it because the VM reference is stable across recomposition. — drafted from issue #145

- [ ] **`retry()` pattern for `flatMapLatest`-shaped VMs**: a `MutableStateFlow<Int>` retry trigger combined into the upstream via `flatMapLatest` is the established way to re-run failed loads in this codebase. Captures the cancel-in-flight + restart semantics for free. — drafted from issue #145
