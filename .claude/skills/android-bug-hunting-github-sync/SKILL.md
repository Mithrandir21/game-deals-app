---
name: android-bug-hunting-github-sync
description: >
  Reconciles a fresh Android bug-hunt report against existing GitHub issues, then files only
  the genuinely new findings. Auto-skips findings that clearly match an open issue, surfaces
  ambiguous matches for human judgment, and flags findings that match a closed issue as
  potential regressions (without auto-reopening). Use this skill after running the
  android-bug-hunting-dispatcher (or any skill that produced an `android-bug-hunt-report.md`)
  whenever the user asks to "file these as issues", "create GitHub issues from the report",
  "sync bug hunt to GitHub", "open issues for these findings", "dedupe and file", or any
  phrasing that asks to turn a bug-hunt report into GitHub issues. Trigger on casual
  phrasings like "open tickets for these", "push these into GitHub", "log these as issues
  but don't dupe what's already there". This is the entry point for the file-issues phase —
  do not invoke `gh issue create` directly outside this skill once a bug-hunt report exists.
---

# Android Bug Hunt — GitHub Sync

## Purpose

The dispatcher produces `android-bug-hunt-report.md`. Without this skill, turning that report
into GitHub issues is a manual one-off — and re-running the dispatcher next month would
re-file every finding as a duplicate.

This skill closes the loop. It:

1. Parses the bug-hunt report into structured findings.
2. Pulls the current open + closed `bug-hunt`-labeled issues via `gh`.
3. Scores each finding against existing issues to classify as **auto-skip**, **ambiguous**,
   **regression candidate**, or **new**.
4. Writes a triage plan, waits for the user to approve (and optionally re-classify),
   then files only the approved items via `gh issue create`.

It is intentionally **standalone** — invoked after the user has reviewed the bug-hunt
report, not chained automatically off the dispatcher. The user wants a beat between phases.

---

## Step 0 — Preconditions

Bail fast if any of these fail. **Write nothing** until all preconditions pass.

```bash
# gh present and authed
gh auth status

# Latest report present
test -f .claude/bug-hunt-workspace/android-bug-hunt-report.md
```

Then verify the label scheme. If any of the labels below is missing, create it with the
matching color before proceeding (so issues land with consistent labelling):

| Label                | Color    | Description                                  |
|----------------------|----------|----------------------------------------------|
| `bug-hunt`           | `B60205` | Surfaced by the android-bug-hunt skill audit |
| `severity:high`      | `B60205` | Bug hunt severity: High                      |
| `severity:medium`    | `FBCA04` | Bug hunt severity: Medium                    |
| `severity:low`       | `FEF2C0` | Bug hunt severity: Low                       |
| `effort:trivial`     | `C2E0C6` | <30m, single-file change                     |
| `effort:small`       | `BFD4F2` | <4h, light testing                           |
| `area:*`             | `5319E7` | One per module/concern (compose, coroutines, …) |

```bash
# Snapshot existing labels; create only what is missing.
gh label list --limit 200
```

For an `area:*` label not yet present (a new module surfaced this run), create it with
color `5319E7` and a one-line description. Never rename existing labels.

Last: warn the user if the working tree is dirty in a way that could distort path matching
(`git status --porcelain` returns non-empty in directories named in the report). Don't
abort — just surface it.

---

## Step 1 — Parse the report

Extract every `BUG-NNN` block from `android-bug-hunt-report.md`. The dispatcher emits each
finding in this canonical form:

```markdown
### BUG-001: <Short title>

| Field | Value |
|---|---|
| **Severity** | Critical / High / Medium / Low |
| **Category** | "Memory leak", "Race condition", "Lifecycle violation", … |
| **Location** | `path/to/File.kt:LINE` |
| **Effort** | Trivial / Small / Medium / Large |
| **Confidence** | High / Medium / Low |

**Description.** …
**Impact.** …
**Evidence.** …
**Recommended fix.** …
**Confidence rationale.** …
```

For each finding, build a record:

```jsonc
{
  "id": "BUG-001",
  "title": "WebView never destroyed",
  "severity": "High",            // → severity:high
  "category": "Resource leak",
  "effort": "Trivial",           // → effort:trivial
  "confidence": "High",
  "locations": ["feature/webview/.../WebView.kt:81-108", ...],
  "areaLabel": "area:webview",   // see mapping below
  "severityLabel": "severity:high",
  "effortLabel": "effort:trivial",
  "body": "<full markdown of the finding, verbatim>"
}
```

**Parsing tolerance.** The dispatcher format may evolve. If the parser can't find one of
the fields above, fall back to `null` rather than aborting — but log which findings were
partially parsed so the user can fix the report.

**Effort mapping.** `Medium` and `Large` from the report do **not** have matching
`effort:*` labels yet. If a finding lands there, omit the `effort:*` label entirely and
note it in the triage plan ("no effort label — `Medium` not in scheme").

**Severity mapping.** `Critical` is treated as `severity:high` (the scheme has no
`severity:critical`). Note the downgrade in the plan.

### Area-label inference

Use the path tokens first; fall back to category text. Keep this table inside the SKILL.md
so it's edited exactly the same way the rest of the skill is.

| Path / category contains              | `area:*` label       |
|---------------------------------------|----------------------|
| `feature/webview`, "WebView"          | `area:webview`       |
| `@Composable`, `feature/*/ui/`, "Compose", "recomposition" | `area:compose` |
| `Flow`, `StateFlow`, `MutableStateFlow`, `viewModelScope`, "coroutine" | `area:coroutines` |
| `Activity`, `Fragment`, `lifecycle`, "leak"                | `area:lifecycle` |
| `Pager`, `PagingSource`, `RemoteMediator`                  | `area:paging`    |
| `common/ui/theme`, "Theme"                                 | `area:theme`     |
| `Storage`, `SharedPreferences`, `DataStore`                | `area:storage`   |

If multiple match, prefer the most specific (`area:webview` over `area:compose` for a
WebView composable). If none match, omit the `area:*` label and surface this in the plan
("no area label inferred — consider creating a new `area:*` label").

---

## Step 2 — Fetch existing issues

```bash
gh issue list \
  --label bug-hunt \
  --state all \
  --limit 200 \
  --json number,title,state,stateReason,labels,body,url
```

Cache to memory. Split into:
- `open` — `state == "OPEN"`
- `closed` — `state == "CLOSED"`, with `stateReason` retained (`completed`, `not_planned`,
  `reopened`, …)

If `--limit 200` is hit, raise it and re-fetch. The matcher needs the full set.

---

## Step 3 — Match findings to existing issues

For each finding, score every candidate existing issue. Run **open** and **closed**
candidates through the same scorer, then branch on state for the outcome.

### Scoring

| Signal                                                                 | Weight             |
|------------------------------------------------------------------------|--------------------|
| Shared `path:` (ignoring `:line`) appears in candidate body            | **+3 per shared path** |
| Category-token overlap in title (see token list below)                 | **+2** (cap once)  |
| Matching `area:*` label                                                | **+1**             |
| Matching `severity:*` label                                            | **+1**             |

**Path matching.** Strip `:LINE` and `:LINE-LINE` suffixes before comparing. A finding at
`Foo.kt:142` matches an issue body referencing `Foo.kt:138`. Exact path-string match only
— do **not** match on basenames alone (`WebView.kt` would collide across modules).

**Category tokens.** Maintain this token list in the SKILL.md. Match case-insensitively
in the candidate's title:

```
WebView · stateIn · WhileSubscribed · MutableStateFlow · asStateFlow · Paging · RemoteMediator ·
recomposition · LaunchedEffect · DisposableEffect · SideEffect · rememberSaveable ·
collectAsState · collectAsStateWithLifecycle · repeatOnLifecycle · flowWithLifecycle ·
SharedPreferences · DataStore · Storage · Theme · Coil · OkHttp · Retrofit · Sandwich ·
viewModelScope · Hilt · Room · DAO · Crashlytics
```

A finding's "category tokens" are derived from its title + `category` field. Score `+2`
if any token from the finding appears in the candidate's title (cap at +2 — don't compound).

### Classification

After scoring, take the highest-scoring candidate (`top`) and the second-highest
(`second`):

- **Strong match**: `top.score >= 5` AND `top.score - second.score >= 2`.
- **Ambiguous**: `top.score >= 3` AND not strong.
- **No match**: `top.score < 3`.

Then branch on candidate state:

| Verdict                       | State   | Outcome                                                        |
|-------------------------------|---------|----------------------------------------------------------------|
| Strong match                  | open    | **Auto-skip** — record `skip:dup-of-#NN`                       |
| Strong match                  | closed  | **Regression candidate** — `regression?:closed-#NN`            |
| Ambiguous                     | open or closed | **Surface for review** — include top 1–3 candidates with scores |
| No match                      | —       | **New** — will be filed                                        |

**Closed-issue weighting.** If a closed candidate has `stateReason: not_planned`, that's a
stronger "we deliberately won't fix this" signal than `completed`. Show the `stateReason`
in the plan; never auto-reopen.

**Module-rename fallback.** If a finding produces zero path-match scores against any
candidate (top score is purely from title/labels), run
`git log --follow --oneline -- <one of the finding's paths>` once and check whether the
file was renamed from a path that *does* appear in any candidate. If so, surface that
candidate at +3 with a `(rename of <old-path>)` note. Do this lazily — only for findings
with zero path matches, not for every finding.

---

## Step 4 — Write the triage plan

Write `.claude/bug-hunt-workspace/github-sync-plan.md`. Overwrite if it exists (the
previous plan is no longer the current state):

```markdown
# GitHub Sync Plan — <YYYY-MM-DD>

Source report: `.claude/bug-hunt-workspace/android-bug-hunt-report.md`
Existing issues fetched: <N> open, <M> closed (label `bug-hunt`)

## Summary
- Findings: N
- Auto-skip (open dup): A
- Regression candidates (closed match): R
- Ambiguous (need a call): M
- New (will be filed on approval): K
- Parse warnings: P
- Label warnings: L

## Auto-skipped (open duplicates)
- BUG-001 — WebView never destroyed → #30 (score 7; shared path `feature/webview/.../WebView.kt`, title token `WebView`, area, severity)
- …

## Regression candidates (closed match — confirm before reopen)
- BUG-NNN — <title> → closed #45 (`completed`, score 6)
  - Suggested action: comment on closed #45 with the new evidence, OR file new with body prefix `> Possible regression of #45.`
  - Why surfaced: <signals>
  - **Default if you don't override below: comment-only (no new issue)**

## Ambiguous (need a call)
- BUG-NNN — <title>
  - Candidate 1: #37 (score 4; shared path `…HomeViewModel.kt`)
  - Candidate 2: #38 (score 3; matching title token `stateIn`)
  - **Default if you don't override below: file new**

## New (will be filed on approval)
- BUG-NNN — <title>  ·  severity:X · effort:Y · area:Z
  - <one-line tagline>

## Parse / label warnings
- BUG-NNN parsed without effort label (report said `Medium`, not in scheme)
- BUG-NNN: no `area:*` label inferred — consider creating one
- BUG-NNN: severity downgraded `Critical` → `severity:high`

---

## How to override

Edit this file, then re-invoke the skill (Step 5 reads this file fresh):

- To **skip** an item, move it to a `## User-skipped` section (any heading containing
  "skip" works).
- To **promote** a regression candidate to a new issue, move it under `## New` and
  optionally add `prefix-with-regression-note: yes` on the next line.
- To **reclassify** an ambiguous to skip, move it under `## User-skipped` with a
  one-line reason.
- Any item left in its original section is treated per its default action above.
```

Present the plan path to the user in chat in one short message. **Stop and wait.** Do
not proceed to Step 5 without explicit approval. Phrases that count as approval: "go",
"file them", "ship it", "looks good", "approved". Phrases that don't: "looks ok"
(without an action verb), questions, anything ambiguous — ask back.

---

## Step 5 — Apply the plan

Re-read `.claude/bug-hunt-workspace/github-sync-plan.md` (so user edits stick).

Build the action list:
- Everything under `## New` → file a new issue.
- Everything under `## Regression candidates` left in place → comment on the closed
  issue with the new evidence; do **not** open a new issue (default action).
- Anything moved under `## New` from regression → file a new issue with body prefix:
  `> Possible regression of #NN (closed <stateReason>).`
- Anything under `## User-skipped` (or any heading containing "skip") → skip silently.
- Anything under `## Auto-skipped` → skip silently (already handled in Step 3).

### Filing a new issue

Heredoc the body to a temp file (avoids shell-escaping pitfalls with backticks, `$`,
quotes, code fences):

```bash
BODY_FILE=$(mktemp /tmp/bughunt-body-XXXXXX.md)
cat > "$BODY_FILE" <<'EOF'
<the finding's full markdown body, verbatim from the report>
EOF

gh issue create \
  --title "<title>" \
  --label "bug,bug-hunt,<severityLabel>,<effortLabel>,<areaLabel>" \
  --body-file "$BODY_FILE"

rm -f "$BODY_FILE"
```

If a label is missing for a given finding (e.g. no `effort:*` because `Medium`), drop it
from the comma list — don't substitute. Report it in the warnings.

### Commenting on a closed issue (regression default)

```bash
COMMENT_FILE=$(mktemp /tmp/bughunt-comment-XXXXXX.md)
cat > "$COMMENT_FILE" <<'EOF'
The android-bug-hunting-dispatcher surfaced this issue again on <YYYY-MM-DD>. Possible regression — please review:

<the finding's full markdown body, verbatim>
EOF

gh issue comment <NN> --body-file "$COMMENT_FILE"
rm -f "$COMMENT_FILE"
```

### Crucial: consult `.claude/lessons.md` before recommending refactors

Before filing each issue, scan `.claude/lessons.md` (Active section only) for any lesson
whose Tags overlap with the finding's category or paths. If a lesson contradicts the
finding's "Recommended fix", surface it inline in the issue body under a `> Note:`
blockquote — do not silently file an issue that recommends something the project has
already deliberately rejected.

Worked example from this codebase: `L-2026-04-30-04` says "keep ViewModel functions
Flow-shaped — don't lower to `viewModelScope.launch { try/catch }`". A finding whose
recommended fix proposes that lowering must include a `> Note:` linking back to the
lesson and offering a Flow-shaped alternative.

---

## Step 6 — Sync log

Append to `.claude/bug-hunt-workspace/github-sync-log-<YYYY-MM-DD>.md` (one file per
calendar day; multiple invocations same day append to the same file):

```markdown
# GitHub Sync Log — <YYYY-MM-DD HH:MM>

Source report: `.claude/bug-hunt-workspace/android-bug-hunt-report.md`

- BUG-001 → skipped (dup of #30)
- BUG-002 → skipped (dup of #31)
- BUG-007 → filed as #50 — https://github.com/owner/repo/issues/50
- BUG-012 → regression candidate; commented on closed #22
- BUG-013 → ambiguous; filed new (#51) per user override
- BUG-019 → user-skipped (`reason: covered by #44 already`)

## Failures (RESUMABLE)
<empty if all succeeded>
```

The log is the **resume point**. If `gh` fails partway through, every successful action
is recorded above the `## Failures` section; subsequent re-invocations of this skill
must read this log first and skip any `BUG-NNN` already marked as `filed` or `skipped`.

### `## Failures (RESUMABLE)` section

If `gh issue create` or `gh issue comment` fails (rate limit, network, malformed body),
write:

```markdown
## Failures (RESUMABLE)
- BUG-007 — `gh issue create` failed: <stderr>
  - Body file preserved: /tmp/bughunt-body-XXXXXX.md
  - Resume: re-invoke this skill; it will pick up here.
```

Then **stop**. Don't continue with the rest of the action list — surface the failure to
the user. The next invocation reads the log, skips BUG-001..BUG-006 (already done),
and retries BUG-007 onward.

---

## Step 7 — Final report to user

One short message. No prose preamble:

```
Filed: K new issues — <urls, one per line>
Skipped: A duplicates of open issues
Regression comments: R on closed issues
Failures: F (see log for resume)
Log: .claude/bug-hunt-workspace/github-sync-log-<date>.md
```

---

## Sharp edges

- **Line drift**: a finding at `Foo.kt:142` matches an existing issue's `Foo.kt:138`.
  Path matching strips `:line` suffixes — see Step 3.
- **Consolidation/split**: if one finding overlaps two open issues with similar scores,
  it is *ambiguous* by definition. Surface both candidates; do not pick.
- **Title drift**: never match purely on title. Step 3's classifier requires
  `top.score >= 3` to be ambiguous and `>= 5` to be strong; either way at least one
  shared path or a +2 token bump is required (a label-only +2 cannot reach the
  ambiguous threshold).
- **`stateReason` distinction**: closed `not_planned` is a stronger don't-refile signal
  than `completed`. Surface both verbatim; never auto-reopen.
- **Module renames**: handled lazily in Step 3 via `git log --follow` only for findings
  with zero path matches.
- **Vocabulary creep**: the category-token list lives in this SKILL.md (Step 3). When
  the codebase grows new vocabulary, edit the list here.
- **Idempotency across runs**: re-running the skill on the same report after partial
  failure must read the latest `github-sync-log-*.md` first and treat any `BUG-NNN`
  marked `filed` or `skipped` (including `user-skipped`) as already-handled.
- **Local `BUG-NNN` identity**: BUG numbers are per-report, not global. Do not try to
  globalize them across runs — once a finding is filed, the GitHub issue number is the
  global identity.
- **Body verbatim**: the finding's markdown body is filed verbatim. If the dispatcher
  ever drops a section (e.g. omits `Confidence rationale`), file what's there. Don't
  fabricate missing sections.
- **Workspace files**: `.claude/bug-hunt-workspace/github-sync-plan.md` and
  `github-sync-log-*.md` live alongside the dispatcher's output. If the project's
  `.gitignore` doesn't already exclude `*-log-*.md` under that directory, mention it
  to the user once (don't auto-edit `.gitignore`).

---

## Anti-patterns — avoid these

- **Auto-reopening closed issues.** A close is a deliberate signal. Surface, comment,
  but never `gh issue reopen`.
- **Filing without a triage plan.** Step 4's plan is the user's veto point. Even on a
  "smoke test" run that produces zero new issues, write the plan and report it.
- **Matching on basename only.** `WebView.kt` exists in many modules. Always match on
  the full repo-relative path.
- **Re-deriving labels per run.** The label scheme is in Step 0. Don't invent new
  conventions per invocation. New `area:*` labels are fine; new severity tiers or
  effort buckets are not.
- **Hiding parse failures.** If a finding can't be fully parsed, surface it in the
  plan's "Parse warnings" section — don't silently drop it.
- **Skipping the lessons-file consultation.** Step 5 explicitly checks
  `.claude/lessons.md` before filing. Skipping this is how stale recommendations make
  it back into issues we've already rejected (`b41c34d` / `4f20fa5` is the cautionary
  tale).
