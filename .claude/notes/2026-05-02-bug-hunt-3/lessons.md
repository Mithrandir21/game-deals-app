# Campaign lessons — 2026-05-02-bug-hunt-3

## Campaign-only

- **(wave 1, #97 Giveaways)** When fixing a "long-lived collector killed by outer .catch" defect in a `combine`-shaped flow, prefer routing inner-flow throws through an *existing* trigger/sentinel value (e.g. `refreshOutcomeFlow.value = Error`) over adding a new parallel error channel — it preserves the single-UI-surface contract and the existing `Error → ERROR` mapper does the rest. Caveat: a Room observation failure won't auto-recover until the user re-triggers (sets refresh to Idle or changes params), which mirrors prior semantics of the buggy outer catch.
- **(wave 1, #98 Giveaway)** When a domain-model file declares `@Immutable` on *some* of its types (`GiveawaySearchParameters` in this case) but not the headlining type (`Giveaway`), the missed annotation is more suspicious than a uniformly-bare file would be — the import is already present and the inconsistency is intra-file, not across the package. Useful signal for future Compose-stability audits.
- **(wave 1, #96 worktree mis-edit)** A worker agent operating in a worktree can still resolve absolute paths back to the parent repo and mis-edit it before noticing. Two mitigations worth considering for `github-issue-waves`: (a) the per-issue prompt could `cd` into `$PWD` and remind the worker to confirm pwd before editing; (b) the orchestrator's sanity-check phase already verifies parent working tree is clean — consider failing fast on stray edits. The agent here self-recovered, so this is not a current incident, just a shape worth instrumenting.

## Promotion candidates (project-wide)

_None._ This wave's fixes followed established lessons (L-2026-04-30-04 for #96 Flow-shaped error handling, project-standard `rememberUpdatedState` for #100, sibling-model `@Immutable` for #98) and the `GameViewModel.loadGameDetailsFlow` working pattern for #97. No new project-wide pattern emerged. The campaign-only notes above are too specific or too meta for `.claude/lessons.md`.
