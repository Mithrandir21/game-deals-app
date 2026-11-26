# Campaign lessons — 2026-05-06-bug-hunt-severity-high

## Campaign lessons

- **(wave 1) Issue #122 — `GiveawaysViewModel.reloadGiveaways`:** The fix was a textbook application of project lesson L-2026-05-02-04 ("`catch (Throwable)` blocks containing suspending work must rethrow `CancellationException` first"). Notable that the antipattern survived to a third file (`GiveawaysViewModel`) after the lesson was already established by #71 (`DealDetailsController`) and #31 (`DealsMediator`). Suggests that when a lesson is added based on one or two sites, a project-wide grep for the antipattern is worth doing at lesson-creation time, not just leaving it to the next bug hunt to find.

## Promotion candidates (project-wide)

*(no new candidates — the L-2026-05-02-04 lesson already exists in `.claude/lessons.md` and this campaign's fix only confirmed it.)*

The "grep at lesson-creation time" observation above is a process tip about how to use the lessons system itself, not a code/architecture lesson — it doesn't fit any of the four memory types in this project's auto-memory scheme. Recording it here in case it surfaces again across multiple campaigns; if it does, that's the signal to make it a real lesson.
