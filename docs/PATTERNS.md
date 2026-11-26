# Patterns and Conventions

A living guide to the coding patterns and architectural conventions in this codebase. Written for mid-level engineers; deep dives are flagged for senior readers. Maintained via the `document-patterns` Claude Code skill.

Statuses: `established` (safe default) · `emerging` (1–3 places, not enforced) · `in-transition` (mid-migration) · `deprecated` (don't apply).

These are **opinionated guidelines, not iron rules.** Each entry may carry a `When to deviate` note. Use judgment.

## Categories

| Category                                              | Summary                                                                                | Last surveyed         |
|-------------------------------------------------------|----------------------------------------------------------------------------------------|-----------------------|
| [architecture](patterns/architecture.md)              | KMP module layering, type-safe `@Serializable` navigation, port/adapter remote sources, twin Android/iOS composition roots. | 34b01013 · 2026-05-18 |
| [ui-state](patterns/ui-state.md)                      | Sealed screen state, StateFlow + `WhileSubscribed(5000)`, shared controllers, `koinViewModel()` resolution. | 34b01013 · 2026-05-18 |
| [compose-correctness](patterns/compose-correctness.md) | `LaunchedEffect`, `rememberUpdatedState`, savers, parent-class `@Immutable` trust, stability gate, Coil 3 null-model guard. | 34b01013 · 2026-05-18 |
| [data](patterns/data.md)                              | Repository facades (Koin-bound), `CachedResource` TTL, Sandwich-Ktor `ApiResponse`, Ktor `HttpClient` factory, Room KMP. | 34b01013 · 2026-05-18 |
| [concurrency](patterns/concurrency.md)                | Virtual-time delay operators, `viewModelScope`, `flatMapLatest`, `Job` cancel, iOS dispatcher caveat. | 34b01013 · 2026-05-18 |
| [errors](patterns/errors.md)                          | Sealed `RemoteHttpException` (Ktor-backed), explicit API try/catch wrapping, `expectSuccess` + timeouts, catch-all `HttpException(code)`. | 34b01013 · 2026-05-18 |
| [resources](patterns/resources.md)                    | Room KMP `useWriterConnection` + `immediateTransaction`, singleton Ktor `HttpClient` per vendor, `BufferedReader.use {}`. | 34b01013 · 2026-05-18 |
| [di](patterns/di.md)                                  | Koin `module { single { … } }` per layer, `viewModel { }` + `koinViewModel()`, `named()` qualifiers, twin `startKoin` bootstrap, platform-suffixed modules. | 34b01013 · 2026-05-18 |
| [kmp](patterns/kmp.md)                                | Expect/actual sibling files, KMP source-set anatomy, platform-suffixed DI modules, iOS UIViewController host, Swift interop, shared `composeResources/`. | 34b01013 · 2026-05-18 |
| [testing](patterns/testing.md)                        | `MainDispatcherTest` base class, Mokkery 3.3.0 in commonTest, `MockHttpClient` (Ktor MockEngine), Koin test module overrides, shared fixtures DSL. | 34b01013 · 2026-05-18 |
| [ui-testing](patterns/ui-testing.md)                  | Compose node finder hierarchy: visible text → content description → role; `androidDeviceTest` source-set rename. | 34b01013 · 2026-05-18 |
| [observability](patterns/observability.md)            | Pluggable `Logger` listeners, extension-function call sites, Sentry-KMP sink (Android), platform-specific impls (Logcat / NSLog), Ktor logging via expect/actual. | 34b01013 · 2026-05-18 |
| [build](patterns/build.md)                            | KMP convention plugin family (`kmp.library` / `kmp.library.compose` / `kmp.feature`), version catalog, KSP-only, `IosSimulatorTestSerializer`, R8 + `isShrinkResources` on `:app`. | 34b01013 · 2026-05-18 |

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
