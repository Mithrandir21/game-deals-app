# Game Deals Android — Executive Summary

**Scale:** 18 Gradle modules, ~8,100 LOC Kotlin, single-Activity Compose, Android-only.
**Stack age:** current at the library layer (Kotlin 2.2 K2, Compose BOM 2025.10.01, Retrofit 3, AGP 8.13, KSP for Room). Behind at the build/release layer.
**Overall verdict:** **ADEQUATE**, trending STRONG once the four issues below are addressed.

---

## Top 5 findings

1. **R8 is disabled in `:app` release.** Every module has `isMinifyEnabled = false`. No `proguard-rules.pro` exists. Release ships full bytecode + resources, no obfuscation. ([Critical #1])
2. **Hilt runs through kapt in 14 modules.** Room is on KSP and proves the toolchain works; Hilt was never migrated. Every annotated-class touch pays a kapt tax. ([Critical #2])
3. **Domain models are simultaneously Room `@Entity`, kotlinx-serialization `@Serializable` DTOs, and Compose `@Immutable` view-model surfaces** — same class, three frameworks. Every API change forces a Room migration; every DB change ripples into Compose previews. Combined with `fallbackToDestructiveMigration()`, this is a slow-burning correctness risk and the structural blocker for KMP. ([Critical #3])
4. **The in-house `Logger.fatalThrowable` doesn't reach Crashlytics.** `SimpleLoggingListener.onFatalThrowable` ends at `Log.wtf(...)`. The infrastructure exists; the wire was never connected. Crashlytics still catches uncaught exceptions, but anything routed through the project's own logging is invisible in production. ([Critical #4])
5. **WebView is JS-enabled with no URL allowlist on a manifest that permits cleartext traffic.** A CheapShark open-redirect (or any future change letting external code pass a URL into the nav route) lands in a JS-enabled, mixed-content-permitted, cleartext-permitted WebView. Realistic phishing/XSS surface. ([Critical #6])

---

## Recommended action sequence

| Sprint | Theme | Highest-leverage items |
|---|---|---|
| 1 | Mechanical, single-PR fixes | Wire Logger→Crashlytics; drop `usesCleartextTraffic`; move `coil-test` to test config; fix the broken `GiveawaysViewModelTest` error case; add Dependabot |
| 2 | Build & release plumbing | Introduce `build-logic/convention/`; migrate Hilt kapt → KSP; enable R8 + resource shrinking; apply or remove the Firebase Performance plugin |
| 3 | UX + perf signal | Edge-to-edge + predictive back; Baseline Profile + Macrobenchmark on Gradle Managed Device; Compose compiler reports + `ImmutableList` + `LazyColumn` keys; LeakCanary/StrictMode in debug |
| 4 | Architectural cleanup | Type-safe Compose Nav; replace WebView with Custom Tabs; DataStore for settings; detekt + Spotless + Kover + GMD-driven instrumented tests in CI; fakes/factories/Turbine |
| 5+ | The model split | Split each entity into `Remote*` / `*Entity` / `*`; convert `:domain` to pure Kotlin; move Room/Paging into a new `:data` module; write real Room migrations with `MigrationTestHelper` |

---

## What to protect

Clean module DAG, consistent feature shape, `internal`-modifier discipline, typed network errors via Sandwich + sealed `RemoteHttpException`, lifecycle-aware Compose state collection across every screen, single Activity, modern result APIs, signing config that supports both local + CI, single `INTERNET` permission, KSP already proven for Room, configuration cache + parallel + daemon enabled, behavior-readable test names. The structural decisions are right. What's missing is the mechanical scaffolding — convention plugins, KSP everywhere, R8, Baseline Profile, real model separation, Crashlytics wire — that the modern Android stack now expects.

See `android-review-report.md` for full evidence, file/line citations, and per-finding recommendations.
