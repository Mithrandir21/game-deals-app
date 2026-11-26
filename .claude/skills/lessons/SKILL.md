---
name: lessons
description: Load and apply the project's accumulated lessons from `.claude/lessons.md` so they inform the rest of the conversation. Use when the user is starting a planning session, kicking off a large feature or refactor, or explicitly asks to "load lessons", "recall lessons", "apply past learnings", "what have we learned", or invokes `/lessons`. Read the `## Active` section only; treat `## Archive` as audit history and consult it only if something looks contradictory.
---

Read `.claude/lessons.md` and internalize every lesson under `## Active`. Ignore the `## Index — Active` block above it — that's a generated human-readable TOC, not a source of lesson content.

For each active lesson, note its **Applies to** scope so you only apply it where relevant. Treat `confirmed` lessons as defaults; treat `tentative` ones as hypotheses to validate against the current code before relying on them.

Do not consult the `## Archive` section unless a current observation contradicts an active lesson and you need history to reconcile it. Never modify `.claude/lessons.md` from this skill — writing/superseding lessons is the `android-retrospective` skill's job.

After loading, briefly tell the user how many active lessons were loaded and which tags they cover, so the user knows what's now in context. Then continue with whatever task prompted the invocation.
