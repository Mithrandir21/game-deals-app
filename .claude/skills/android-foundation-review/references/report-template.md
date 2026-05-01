# Report Template — Final Android Review

Use this structure for the synthesized report. Every section is mandatory unless marked
optional. The audience is a staff-to-principal level Android engineer. Write accordingly:
precise, evidence-backed, opinionated where warranted, pragmatic about trade-offs.

---

## Report Structure

```markdown
# Android Architecture Review — {PROJECT_NAME}

**Reviewed**: {DATE}
**Scope**: {List of modules reviewed}
**Project size**: {Module count, approx LOC, language mix (Kotlin/Java %)}
**Stack signals**: {Compose / Views, KMP yes/no, DI framework, build system version}

---

## Executive Summary

{3–5 sentences. Lead with the single most important finding — positive or negative.
State overall maturity: "production-grade", "solid foundation with gaps", "needs
significant rework", or "prototype-quality". End with the highest-impact recommendation.}

### Scorecard

| Dimension                    | Verdict   | One-line summary |
|------------------------------|-----------|------------------|
| Architecture                 | {verdict} | {1-line}         |
| Layering & Separation        | {verdict} | {1-line}         |
| Modern Patterns & Tooling    | {verdict} | {1-line}         |
| Testing                      | {verdict} | {1-line}         |
| Performance                  | {verdict} | {1-line}         |
| Security & Privacy           | {verdict} | {1-line}         |

Verdict scale: STRONG · ADEQUATE · WEAK · MISSING

{If two agents disagreed on a dimension and the synthesizer resolved it, add a
footnote here:
> Note: Architecture rated DI as STRONG, but Layering & Separation found ViewModels
> bypassing DI in `:feature:onboarding`. Resolved as ADEQUATE — Hilt is correctly
> wired but enforcement is incomplete.}

---

## Strengths

{What the project does well. Be specific — name the pattern, the module, the class.
Explain WHY it's good and what it enables. Engineers reading this section should know
what to protect during refactoring.

Minimum 2 items, no upper limit. Each item is a paragraph with a bolded lead-in.}

---

## Critical Findings

{Architectural flaws, security holes, or performance traps that will compound as the
team or user base scales. "Fix this quarter" items.

For each finding:
- **Title**: Clear, specific name for the issue.
- **Evidence**: File paths, class names, code patterns observed. Concrete.
- **Impact**: What goes wrong if this isn't addressed. Connect to team productivity,
  bug rate, onboarding friction, scalability, or user harm.
- **Recommendation**: Specific, actionable steps. Not "add tests" but "extract
  `ProcessPaymentUseCase` from `PaymentViewModel`, write unit tests covering the
  three payment states (success, decline, network failure), inject a
  `FakePaymentRepository` from a `:test-fixtures` module."
- **Effort estimate**: T-shirt size (S/M/L/XL) with brief justification.}

---

## Significant Findings

{Same format as Critical, but "fix this half" items. They slow the team or risk
users but don't create structural risk.}

---

## Refinements

{Polish items. Fix during a free sprint. Same format but Recommendation + Effort
only — skip Impact unless non-obvious.}

---

## Module-by-Module Breakdown (Optional — include for 5+ module projects)

{For each module:
- Purpose (1 sentence)
- Dependencies (what it depends on, what depends on it)
- Key concerns (1–3 bullets)
- Verdict: STRONG / ADEQUATE / WEAK}

---

## Cross-Cutting: Accessibility

{Brief subsection drawn from Modern Patterns and Layering & Separation findings.
What's the project's accessibility posture? What would help most? If no findings
were significant, a single paragraph is enough.}

---

## Cross-Cutting: Observability

{Brief subsection drawn from Architecture and Testing findings. Crash reporting,
logging discipline, performance monitoring, ANR tracking. What's instrumented,
what isn't, and what blind spots matter most.}

---

## Testing Deep-Dive

{Dedicated section because testing is complex enough to warrant its own treatment.}

### Coverage Shape
{Pyramid, diamond, or ice cream cone? Unit vs integration vs UI breakdown with numbers.}

### Test Quality Assessment
{Based on sampling actual test files. Quote (briefly) the best and worst test you
found. Explain what makes them good or bad.}

### Infrastructure
{CI, coverage tools (Kover/JaCoCo), static analysis (detekt, ktlint, Konsist), Gradle
Managed Devices or Firebase Test Lab. What's automated, what's manual, what's missing.}

### Recommended Testing Roadmap
{Prioritized list: what to test first, what framework/library additions would help,
what's the target state.}

---

## Performance Deep-Dive (include if Performance verdict is WEAK or MISSING, or if
the project has known performance pain)

{Dedicated treatment when performance posture is a meaningful issue.}

### Measurement Infrastructure
{Baseline Profiles, Macrobenchmark, JankStats, custom traces — what exists.}

### Highest-Leverage Wins
{Ranked: e.g., "Add a Baseline Profile (M effort, ~20–30% cold-start improvement),
turn on R8 full mode (S effort), add stability annotations to `Cart` (S effort)".}

---

## Security & Privacy Deep-Dive (include if Security verdict is WEAK or MISSING, or
if the app handles sensitive data)

### Threat Posture
{What's the app domain implying about sensitivity? What does the storage and network
posture look like against that?}

### Highest-Leverage Wins
{Ranked. Same format as Performance.}

---

## Recommended Action Sequence

{Ordered list of what to do first, second, third. The "if you only read one section"
part of the report. Each item should reference the finding it addresses.

Format:
1. **{Action}** — {Why first}. Addresses: {Finding title}. Effort: {T-shirt}.
2. ...

Cap at 7 items. If there are more findings, the first 7 should be the ones that
unblock the most progress.}

---

## Appendix: Tools & Versions Observed

{Table of key dependency versions found in the project. Useful for quickly assessing
how current the project is.}

| Tool / Library         | Version Found | Latest Stable | Notes |
|------------------------|---------------|---------------|-------|
| Kotlin                 | x.y.z         | (latest)      |       |
| AGP                    | x.y.z         | (latest)      |       |
| Gradle                 | x.y           | (latest)      |       |
| Compose BOM            | yyyy.mm.dd    | (latest)      |       |
| Compose Compiler       |               |               |       |
| Hilt / Koin            |               |               |       |
| Room / SQLDelight      |               |               |       |
| Retrofit / Ktor        |               |               |       |
| Coroutines             |               |               |       |
| ...                    |               |               |       |
```

---

## Writing Guidelines for the Report

1. **Evidence over opinion.** Every claim must reference a specific file, class, or
   pattern. "The architecture is clean" is useless. "`:core:domain` has zero Android
   dependencies and its 12 use case classes each take a single repository interface as
   a constructor parameter — this is textbook clean architecture" is useful.

2. **Concise doesn't mean vague.** Short paragraphs, but packed with specifics. Every
   sentence earns its place.

3. **Respect the reader's time.** The scorecard + executive summary + action sequence
   should be readable in 2 minutes and give a complete picture. The detailed sections
   are for when they want to dig in.

4. **Stay pragmatic about trade-offs.** If a project chose Koin over Hilt, don't mark
   it as wrong — explain the trade-off (less compile-time safety, simpler KMP story).
   The review should show understanding of the forces that shaped the codebase, not
   reflexive default-matching.

5. **Don't bury the lede.** The most important finding goes in the first sentence of
   the executive summary, not at the bottom of a bulleted list.

6. **Resolve conflicts visibly.** When the agent findings disagreed, briefly explain
   how you resolved it. Don't paper over disagreement — it's a signal that the team
   may have differing mental models of the codebase.

7. **Calibrate by domain.** A todo app and a banking app deserve different security
   verdicts for the same code. Note your calibration assumption when it materially
   changes a finding.
