# Patterns and Conventions

A living guide to the coding patterns and architectural conventions in this codebase. Written for mid-level engineers; deep dives are flagged for senior readers. Maintained via the `document-patterns` Claude Code skill.

Statuses: `established` (safe default) · `emerging` (1–3 places, not enforced) · `in-transition` (mid-migration) · `deprecated` (don't apply).

These are **opinionated guidelines, not iron rules.** Each entry may carry a `When to deviate` note. Use judgment.

## Categories

| Category                                              | Summary                                                                          | Last surveyed         |
|-------------------------------------------------------|----------------------------------------------------------------------------------|-----------------------|
| [architecture](patterns/architecture.md)              | Module layering, type-safe navigation, port/adapter remote sources.              | 31a89bc · 2026-05-03 |
| [ui-state](patterns/ui-state.md)                      | Sealed screen state, StateFlow + `WhileSubscribed(5000)`, shared controllers.    | 31a89bc · 2026-05-03 |
| [compose-correctness](patterns/compose-correctness.md) | `LaunchedEffect`, `rememberUpdatedState`, savers, `@Immutable`, state hoisting. | 31a89bc · 2026-05-03 |
| [data](patterns/data.md)                              | Repository facades, `CachedResource` TTL, Sandwich `ApiResponse`, paging.        | 31a89bc · 2026-05-03 |
| [concurrency](patterns/concurrency.md)                | Virtual-time delay operators, `viewModelScope`, `flatMapLatest`, `Job` cancel.   | 31a89bc · 2026-05-03 |
| [errors](patterns/errors.md)                          | Sealed `RemoteHttpException`, error state variants, `.catch` to UI state.        | 31a89bc · 2026-05-03 |
| [resources](patterns/resources.md)                    | Room `withTransaction`, singleton `OkHttpClient`, `BufferedReader.use {}`.       | 31a89bc · 2026-05-03 |
| [di](patterns/di.md)                                  | Singleton-only Hilt modules, per-vendor qualifiers, `@HiltViewModel` injection.  | 31a89bc · 2026-05-03 |
| [testing](patterns/testing.md)                        | `MainCoroutineRule` + `observeEmissions()`, MockK, MockWebServer, fixture Hilt.  | f215235 · 2026-05-14 |
| [ui-testing](patterns/ui-testing.md)                   | Compose node finder hierarchy: visible text → content description → role.        | f215235 · 2026-05-14 |
| [observability](patterns/observability.md)            | Pluggable `Logger` listeners, extension-function call sites, fatal boundary.     | 31a89bc · 2026-05-03 |
| [build](patterns/build.md)                            | Convention plugins, version catalog, KSP-only, Compose 2.2 wiring, signing.      | 31a89bc · 2026-05-03 |

## How to read these

Each category file is structured the same way:

- **Frontmatter** declares the `Path scope` (which directories the category covers) and the `Last surveyed` cursor (SHA + date).
- **Patterns** are listed newest-documented first. Each entry covers what the pattern is, why it works here, where it strains, how to apply it (pseudocode), and where to find it in the repo.
- **What we don't do** lists anti-patterns the codebase explicitly avoids, with a one-line reason for each.
- **Decommissioned** (when present) holds deprecated entries kept for historical reading.

## Updating

Run the `document-patterns` skill in update mode (`/document-patterns`) to refresh against the current `HEAD`. The skill compares each category's `Path scope` against `git diff <oldest-cursor>..HEAD`, spawns verify and discovery sub-agents in parallel for affected categories, and presents a triage plan before writing anything. Approve or edit; the skill updates files in place and bumps cursors.

For a deep re-survey of a single category, use `/document-patterns --deep <category>`.

## What's out of scope

- Non-code conventions (PR templates, commit messages, branch naming) live in `CONTRIBUTING.md` / `AGENTS.md`.
- Agent-internal docs (`.claude/CLAUDE.md`, `.claude/lessons.md`, `AGENTS.md`) are never modified by this guide.
- Pattern entries may cite a lesson by ID under `Related lessons`, but never inline lesson prose.
