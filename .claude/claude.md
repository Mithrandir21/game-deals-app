@AGENTS.md

## Code comments

Prefer **no comments**. Well-named functions, variables, and types should carry the meaning on their own. When a comment *is* useful, keep it short.

Guidelines:
- **Target one line, max two.** If you find yourself writing a 3+ line inline block, the comment is too long — trim it, don't expand it. The code itself is usually fine; the comment is what needs to shrink.
- When you do comment, explain **why**, not **what**. The code already shows *what* it does; comments earn their place by capturing a hidden constraint, a subtle invariant, a workaround, or behavior that would surprise a reader.
- KDoc / Javadoc on public API is fine — keep it concise (1–2 lines is usually plenty). Avoid multi-paragraph docstrings unless the API genuinely needs them.
- Don't reference the current task, PR, or issue number in source comments ("added for X", "fixes #123"). That context belongs in the commit message and PR body.
- Don't leave breadcrumbs like `// removed`, `// was: ...`, `// TODO from old design`. Delete cleanly.
