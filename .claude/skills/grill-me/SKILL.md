---
name: grill-me
description: Interview the user relentlessly about a plan or design until reaching shared understanding, resolving each branch of the decision tree. Use when user wants to stress-test a plan, get grilled on their design, or mentions "grill me".
---

Interview me relentlessly about every aspect of this plan until we reach a shared understanding. Walk down each branch of the design tree, resolving dependencies between decisions one-by-one.

## How to ask

Use the `AskUserQuestion` tool — never dump a wall of questions in a chat message. The point is to give me concrete choices instead of open-ended prose.

- You may put **1–4 questions in a single `AskUserQuestion` call**. Group questions when they're truly independent (no later answer depends on an earlier one in the same call). Don't pad just to fill slots.
- If a later question's options depend on the answer to an earlier one, ask the earlier one alone first, then send the next call once you have the answer.
- Each question must offer **2–4 pre-defined options** that cover the realistic answer space. Make options mutually exclusive and concrete (name the library, the pattern, the file — not vague directions).
- **Do not add an "Other" option yourself** — the tool appends one automatically for free-text answers.
- If you have a recommendation, put it as the first option and append "(Recommended)" to the label. Use the `description` field to explain the trade-off, not just restate the label.
- Keep `header` under 12 chars (e.g. "Auth method", "Caching", "Error UX").
- Use `multiSelect: true` only when the choices genuinely combine (e.g. "which surfaces should this affect?"). Single decisions stay single-select.
- Use `preview` when the choice is between concrete artifacts worth eyeballing — code snippets, ASCII layouts, config blocks. Skip it for plain preference questions.

## How to drive the interview

- Walk the decision tree depth-first. Resolve a parent decision before asking about anything that depends on it.
- After each batch of answers, restate what was decided in one short sentence, then send the next `AskUserQuestion` call. Don't lecture.
- If a question can be answered by reading the codebase, **read the codebase instead of asking** — then tell me what you found and ask only the residual question that the code can't settle.
- Stop when the remaining branches are either trivial or already implied by earlier answers. Say so explicitly; don't manufacture filler questions.
