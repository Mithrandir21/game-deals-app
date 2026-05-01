# Android Bug-Hunting Skills

A bundle of nine Claude Skills focused on **finding bugs** in modern Android (and KMP)
codebases. Distinct from a code review or architecture audit — these are oriented
toward concrete, runtime-affecting defects with high signal-to-noise.

## The skills

| Skill | What it finds |
|---|---|
| **android-bug-hunting-dispatcher** | Top-level dispatcher. Profiles the project, fans out to relevant specialists, aggregates and ranks findings. Use this first. |
| **android-bug-hunting-coroutine-and-flow-defects** | `GlobalScope`/`runBlocking` misuse, missing `repeatOnLifecycle`, `StateFlow` for one-shot events, missing `flowOn`, blocking calls inside coroutines, non-cooperative cancellation. |
| **android-bug-hunting-lifecycle-leak-hunter** | Static/singleton refs to `Context`/`Activity`/`View`, ViewBinding accessed past `onDestroyView`, listeners registered without unregister, Handler/inner-class leaks, ViewModels holding Activity refs. |
| **android-bug-hunting-compose-correctness** | Side effects launched outside `LaunchedEffect`, `LaunchedEffect(Unit)` when keys should depend on state, `rememberSaveable` with non-Saveable types, unstable parameters causing recomposition storms, `collectAsState` vs `collectAsStateWithLifecycle`. |
| **android-bug-hunting-main-thread-violations** | Synchronous Room DAOs, network calls on Main, `SharedPreferences.commit()`, file I/O on Main, `BitmapFactory.decode*` on Main, JSON parsing on Main. |
| **android-bug-hunting-resource-leaks** | Unclosed `Cursor`/`InputStream`/`Reader`, unclosed OkHttp `Response`, `TypedArray` not recycled, `MediaPlayer`/`MediaRecorder` not released, `ImageProxy` not closed, leaked database transactions. |
| **android-bug-hunting-kmp-defects** | Android types in `commonMain`, `expect`/`actual` placement issues, `java.time` in common, `runBlocking` in shared code, `Dispatchers.Main` without target dispatcher artifact, Swift-interop hazards. |
| **android-bug-hunting-stacktrace-analyzer** | Pattern-matches a stacktrace against a catalogue of common Android crash causes and points to the suspect file:line with a high-confidence hypothesis. |
| **android-bug-hunting-anr-trace-analyzer** | Reads ANR traces, classifies the cause (I/O, lock contention, deadlock, JNI, infinite loop, MessageQueue starvation), traces the contention graph across threads. |

## Shared bug report format

Every specialist produces findings in the same format so the dispatcher can
aggregate and rank them. Each bug includes:

- **Severity** — Critical / High / Medium / Low
- **Category** — e.g. "Memory leak", "Race condition"
- **Location** — `file:line` or `Class.method`
- **Effort** — Trivial / Small / Medium / Large
- **Confidence** — High / Medium / Low
- **Description** — what's wrong
- **Impact** — what happens at runtime
- **Evidence** — minimal code snippet
- **Recommended fix** — concrete change
- **Confidence rationale** — why we're sure (or not)

The full rubric for each scale is defined in `android-bug-hunting-dispatcher/SKILL.md`.

## How to use

The intended entry point is `android-bug-hunting-dispatcher`. It:

1. Profiles the project (modules, source sets, libraries in use).
2. Decides which specialists to dispatch — e.g. only runs `android-bug-hunting-compose-correctness` if
   `@Composable` is present, only runs `android-bug-hunting-kmp-defects` if `commonMain` exists.
3. Spawns the specialists in parallel.
4. Aggregates, deduplicates, renumbers, and severity-ranks findings.
5. Produces a single report.

You can also invoke any specialist standalone — each is self-contained and uses the
same output format.

`android-bug-hunting-stacktrace-analyzer` and `android-bug-hunting-anr-trace-analyzer` are different in shape: they take a
trace as input and produce a *single hypothesis* rather than a list of findings.
They're triggered by the user pasting a trace, not by a code audit.

## Installation

These are skill folders. Drop them into wherever your Claude environment loads skills
from (typically `~/.claude/skills/` or your repo's `.claude/skills/`), or zip them
up and load via your skill installer of choice.

## Notes on scope

- **Modern Android.** These skills assume Kotlin, AndroidX, coroutines + Flow, and
  optionally Compose. Patterns from older eras (RxJava, AsyncTask, support library)
  are mentioned but not the focus.
- **High signal-to-noise.** Each detector specifies what to grep for, what to verify
  in context, and the false-positive risks. The skills favor fewer high-confidence
  findings over many speculative ones.
- **Not a code review.** If the user wants architecture or style commentary, point
  them to a review skill instead. These skills produce defect reports.
