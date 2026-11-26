# Kotlin 2.3.x upgrade — findings & deferred plan

> **Archived 2026-05-18.** This deferred-plan / findings document was executed and is now a sealed historical reference. See [`docs/archive/kotlin-2.3-upgrade-findings.md`](archive/kotlin-2.3-upgrade-findings.md) for the original investigation, trade-off analysis, and migration costs. The actual landed work is in:
>
> - **PR #169** — Kotlin 2.2.21 → 2.3.21, AGP 9.0.1 → 9.1.1 (`com.android.kotlin.multiplatform.library`), CMP 1.10.3 → 1.11.0, Mokkery 2.10.2 → 3.3.0, KSP 2.3.2 → 2.3.8, Ktor 3.3.0 → 3.4.3, Sentry-KMP 0.13.0 → 0.26.0, Compose BOM 2026.05.00.
> - **PR #173** — Compose Stability Analyzer 0.7.5 + CI gate (`debugStabilityCheck`).
> - **PR #174** — R8 minification on `:app` release (21 MB → 6.9 MB), eight `@Immutable` annotations dropped (parent-trust), Mokkery 3.x DSL touch-up, store-banner null guard, R8 mapping runbook.
>
> Durable learnings from the migration are captured as `L-2026-05-17-01..16` and `L-2026-05-18-01..03` in [`.claude/lessons.md`](../.claude/lessons.md).
