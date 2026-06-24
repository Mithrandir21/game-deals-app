# Campaign lessons — 2026-05-01-bug-hunt-severity-low

## Campaign lessons

- (wave 1) Mid-wave worktree pruning: the agent for #47 reported its worktree
  was pruned and had to be re-registered with git mid-task. No work was lost,
  but if this becomes a pattern, the orchestrator might need to call
  `git worktree prune` upfront or add a retry hook. One occurrence so far —
  not yet a clear signal.
- (wave 1) Same-file scope discipline worked: #48 and #44 both target
  `common/ui/.../Theme.kt`. Splitting them across waves and explicitly telling
  #48's agent to leave #44's block alone produced a cleanly scoped 6/5-line
  diff. The "leave the other issue's lines alone" instruction was load-bearing.
- (wave 2) Agent transparency on scope expansion: the #45 agent added
  `common/build.gradle.kts` (test dependency) outside its pre-cleared file
  set, but flagged it explicitly in its return report with the reason. This
  is the right behavior — the skill's "pre-cleared file set" model is a
  hint, not a hard fence; what matters is that the agent is honest about
  what it touched. Worth keeping the "if you must expand scope, return the
  reason" instruction in future agent prompts.

## Promotion candidates (project-wide)

- [x] **Don't add `.catch` to Paging Flows after `.cachedIn`.** It swallows
  construction-time exceptions and leaves `LazyPagingItems` stuck on cached
  data with no recovery. Paging's `LoadState.Error` is the correct surface
  point for load errors. — drafted from #46. **Promoted as L-2026-05-01-04.**

- ~~**`rememberSaveable` is for state that should survive process death,
  not transient runtime signals.**~~ — drafted from #43. Declined: too
  generic, well-covered in Compose docs.

- ~~**Coil's image dispatcher should be `Dispatchers.IO` (its default).**~~
  — drafted from #47. Declined: generic library advice, no project-specific
  gotcha.

- [x] **Don't measure elapsed time with `System.currentTimeMillis()` inside
  Flow code that also uses `delay()`.** Under `runTest` with a
  `TestDispatcher`, `delay` honors virtual time but `System.currentTimeMillis`
  does not — measured elapsed will be ~0 in tests, distorting any
  "at-least-N-millis" semantics. Use the coroutineScope parallel pattern
  (`async { work }; launch { delay(N) }; await; join`) so all timing is
  virtual-time aware. — drafted from #45. **Promoted as L-2026-05-01-05.**
