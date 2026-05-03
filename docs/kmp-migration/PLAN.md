# KMP / Compose Multiplatform Conversion Plan

## Context

`game-deals-android-app` is currently an Android-only Kotlin/Compose app (~9K LOC, 11 modules, single-activity, Hilt + Room + Retrofit + Coil 2 + Firebase). The owner (solo dev, learning project, not currently released on any store) wants to ship the same app on iOS from a single codebase using **Kotlin Multiplatform + Compose Multiplatform shared UI**.

The intent is the most ambitious shape of this conversion:

- **Convert in place** — restructure existing modules into KMP modules with `commonMain`/`androidMain`/`iosMain` source sets. The Android app may be unbuildable for stretches; that is acceptable because nothing is shipping yet.
- **Shared CMP UI** — one Compose UI tree on both platforms. iOS will look like an Android app (pure Material3 everywhere). Acknowledged trade-off.
- **Solo dev, no deadline.** Realistic timeline 3–6 months of evenings/weekends.

The iOS app is the eventual goal but not the immediate one. The migration succeeds when (a) the Android app reaches feature parity with today's behavior on the new stack, and (b) a runnable iOS Simulator app exists with the same features (minus polish).

## Decisions locked in (from grilling)

| # | Branch | Decision |
|---|---|---|
| A | Migration shape | **A1** — restructure existing modules in place |
| B | Navigation | **`androidx.navigation` 2.9 multiplatform** (Jetbrains) |
| C | WebView on iOS | **C1** — `expect/actual` bridge: Android `WebView`, iOS `WKWebView` |
| D | Database | **D1** — Room KMP (since 2.7), keep existing DAOs |
| E | Crash/perf observability | **E3** — Sentry (replaces Crashlytics + Performance) |
| F | Toolchain | Apple Silicon Mac, Xcode, simulator confirmed |
| G | Timeline | 3–6 months acceptable |
| H | ViewModel | **H1** — `androidx.lifecycle.viewmodel` multiplatform |
| I | Product analytics | **I1** — drop entirely (Firebase Analytics removed, no replacement) |
| J | iOS look-and-feel | **J1** — pure Material3, no Cupertino |
| K | Tests | Keep Android tests as-is; add iOS unit tests later (revisit end of session) |
| L1 | `ApiResponse` wrapper | **`sandwich-ktor`** |
| L2 | Paging | **`androidx.paging` multiplatform** (Jetbrains) |
| L3 | Image loading | **Coil 3** |
| L4 | Datetime formatting | Per-platform `expect/actual` formatter |
| M | Migration order | Leaf-first, confirmed |
| iOS dist | Format | SPM XCFramework |
| iOS shell | Layout | `iosApp/` at repo root, SwiftUI `App` + `ComposeUIViewControllerRepresentable` |
| Resources | Strings/drawables | `compose.resources` |
| CI | iOS CI | Deferred |
| R8 | Minification | Don't add during migration |

## Branching strategy

The migration runs on a long-lived feature branch with one child branch per phase. The plan doc itself is committed to the feature branch and is the blueprint each phase branch follows.

- **Feature branch**: `feature/kmp-migration` (off `dev`).
- **Plan doc location on the branch**: `docs/kmp-migration/PLAN.md` — a copy of this plan, committed to the feature branch as the immutable blueprint. Lives in-repo so every phase branch sees it.
- **Per-phase branches**: `feature/kmp-migration-phase-0-toolchain`, `feature/kmp-migration-phase-1-conventions`, `feature/kmp-migration-phase-2-domain`, `feature/kmp-migration-phase-3-remote`, `feature/kmp-migration-phase-4-di-observability`, `feature/kmp-migration-phase-5-features`, `feature/kmp-migration-phase-6-iosapp`, `feature/kmp-migration-phase-7-longtail`. (Hyphen-separated, not slash-nested, because git refuses to create a branch under another branch's name.) Each is cut from the feature branch.
- **Recovery tags**: `kmp-pre-phase-0` … `kmp-pre-phase-7` cut at the start of each phase on the feature branch, per the regression-evidence section.
- **Merge cadence**: phase branches merge back into the feature branch with `--no-ff` after their golden-journey smoke pass and behavior diff. The feature branch is *not* merged to `dev` until Phase 7 completes and the full verification checklist passes.
- **Phase notes**: each phase keeps a running log at `.claude/notes/kmp-migration/phase-N.md` (created on the phase branch, merged back) capturing decisions, deviations from this plan, and golden-journey results.

The bootstrap sequence (executed immediately after plan approval):

1. Create `feature/kmp-migration` from `dev`.
2. Copy this plan to `docs/kmp-migration/PLAN.md` on that branch and commit it.
3. Cut tag `kmp-pre-phase-0`.
4. Create `feature/kmp-migration-phase-0-toolchain` from the feature branch — Phase 0 work begins there.

## Tech swaps (one-line summary)

- **DI**: Hilt → Koin (big-bang, can't be incremental in this codebase)
- **Database**: Room (Android) → Room KMP — DAOs stay, build wiring changes
- **HTTP**: Retrofit + Sandwich → Ktor Client + sandwich-ktor
- **Datetime**: `java.time` → `kotlinx-datetime` (+ `expect/actual` formatter)
- **Images**: Coil 2.7 → Coil 3 (KMP)
- **Observability**: Firebase Crashlytics/Perf → Sentry; Firebase Analytics dropped
- **Paging**: `androidx.paging:paging-runtime` → `androidx.paging:paging-common-multiplatform`
- **Navigation**: `androidx.navigation:navigation-compose` → `androidx.navigation:navigation-compose-multiplatform`
- **ViewModel**: `androidx.lifecycle:viewmodel-ktx` → `androidx.lifecycle:viewmodel-multiplatform`
- **Resources**: `R.string.*` / `R.drawable.*` → `Res.string.*` / `Res.drawable.*` via `compose.resources`
- **Test infra**: Hilt fixtures → Koin test modules; `MockWebServer` → Ktor `MockEngine`. Android tests keep MockK + Espresso.

## Module shape (after migration)

Every existing library module becomes a KMP module. `:app` stays Android-only as the Android entry point. New `iosApp/` Xcode project consumes the XCFramework.

```
:app                  Android entry point (MainActivity, application class)  [androidMain only]
:base                 KMP — shared logging boot
:common               KMP — utilities, Clock, qualifiers, serializers, datetime expect/actual
:common:ui            KMP — Compose theme, design system, shared components
:domain               KMP — repositories, models, Room KMP DAOs, sources
:logging              KMP — Logger interface + Sentry adapters (Android + iOS)
:remote:cheapshark    KMP — Ktor + sandwich-ktor source impl
:remote:gamerpower    KMP — Ktor + sandwich-ktor source impl
:feature:home         KMP — screen, viewmodel, navigation
:feature:game         KMP
:feature:search       KMP
:feature:store        KMP
:feature:deal         KMP
:feature:giveaways    KMP
:feature:webview      KMP — expect/actual WebView bridge
:testing              KMP — fixtures (Koin test modules; MockK lives in androidTest only)
:build-logic          Convention plugins rewritten for KMP
iosApp/               New — Xcode project, SwiftUI App, consumes :shared XCFramework
```

## Migration sequence (leaf-first)

The risk control: do not start a phase before the previous phase's tests pass on Android.

### Phase 0 — Toolchain + KMP proof-of-life (~1 week)
- Install Xcode, verify simulator runs Hello CMP from JetBrains template.
- Bump Kotlin/Compose versions to a CMP-compatible matrix. Verify Compose Multiplatform 1.7+ (or current stable) compiles against Kotlin 2.2.x.
- Convert `:common` (smallest, no inbound deps from features) into a KMP module as a proof. Validate: Android build still works; iOS framework builds.
- Output: a working `commonMain` source set, no functional changes yet.

### Phase 1 — Convention plugins rewritten (~1 week)
- Rewrite `build-logic/convention/`:
  - `AndroidLibraryConventionPlugin` → `KotlinMultiplatformLibraryConventionPlugin`
  - `AndroidLibraryComposeConventionPlugin` → `KotlinMultiplatformComposeConventionPlugin`
  - `AndroidFeatureConventionPlugin` → `KotlinMultiplatformFeatureConventionPlugin`
  - `AndroidKspConventionPlugin` — refactor for KMP-multiplatform KSP
  - `AndroidApplicationConventionPlugin` — keep, used only for `:app`
- `gradle/libs.versions.toml`: add CMP, Ktor, Koin, Sentry, kotlinx-datetime, Coil 3, Room KMP, paging-multiplatform, sandwich-ktor.
- Output: every existing module compiles under the new conventions, source still in `src/main/java/`.

### Phase 2 — datetime swap (~3-4 days, scope reduced from original)
**Scope reduction (locked in 2026-05-03 during Phase 2 execution):** Original
Phase 2 had two halves: (A) datetime swap, (B) `:domain` → KMP module + Room
KMP. Only (A) is in Phase 2 now; (B) folds into Phase 5.

**Why the deferral**: Room source-in-commonMain depends on Paging
Multiplatform being wired (because `androidx.room:room-paging` is Android-only,
and `:domain`'s `PagingDao` would otherwise have to leak Paging types into the
common surface ahead of schedule). Phase 5 already has to migrate `:domain`
content for the Paging swap. A `:domain` build-shell-only move in Phase 2 is
busywork that gets re-done in Phase 5.

**What Phase 2 does:**
- Migrate `pm.bam.gamedeals.common.datetime.*` (currently `java.time`) to `kotlinx-datetime`.
- Move datetime interfaces + impls from `:common`'s androidMain to commonMain.
- Implement `expect fun formatLocaleAwareDate(Instant): String` with Android (`java.time.DateTimeFormatter`) and iOS (`NSDateFormatter`) actuals — kotlinx-datetime's format builder cannot yet produce locale-aware month abbreviations.
- Keep `Clock` interface in `:common` (already an abstraction — survives well).
- Ripple: `:domain` (TypeAdapters, TypeSerializers, Giveaway model), `:common:ui` (PreviewData), tests in `:common`/`:domain`/`:remote:gamerpower`.
- Output: every `java.time.*` import swapped to `kotlinx.datetime.*`. Android runtime tests pass; iOS targets compile.

**Deferred to Phase 5:**
- `:domain` becomes a KMP module (apply `gamedeals.kmp.library` convention).
- Room source migration to commonMain (`RoomDatabase.Builder` KMP API; `expect` driver factory).
- Source-set restructure: `src/main/java/` → `src/androidMain/kotlin/` and (selectively) `src/commonMain/kotlin/`.

### Phase 3 — `:remote:*` networking swap (~2 weeks)
- Replace Retrofit APIs in `remote/cheapshark/` and `remote/gamerpower/` with Ktor Client.
- Replace Sandwich `ApiResponse` with `sandwich-ktor` equivalent — interface stays the same, body changes.
- `RemoteExceptionTransformer` (sealed `RemoteHttpException` mapper, per `errors.md`) keeps its shape; the input type changes from Retrofit's `Response` to Ktor's response.
- HTTP clients become `expect`-provided per platform (`OkHttp` engine on Android, `Darwin` engine on iOS).
- Output: both `:remote:*` modules compile and run under both targets; `MockWebServer` swapped for Ktor `MockEngine` in tests.

### Phase 4 — DI + observability swap (~1–2 weeks, BIG-BANG)
- Replace Hilt with Koin across every module simultaneously.
  - Each `@HiltViewModel` → Koin `viewModel { ... }` registration.
  - `hiltViewModel()` Compose call sites → `koinViewModel()`.
  - Hilt modules in `:domain`, `:remote:*`, `:app:di`, `:logging` all rewritten as Koin modules.
  - `GameDealsApplication` loses `@HiltAndroidApp`, gains `startKoin { modules(...) }`.
  - `MainActivity` loses `@AndroidEntryPoint`.
  - DI graph for `:app` lives in `androidMain`; shared graph in `commonMain`.
- Replace Firebase Crashlytics / Perf with Sentry.
  - `:logging` Logger interface stays.
  - `LoggingBaseActivity` deleted; Logger initialization moves to `MainActivity.onCreate` (Android) and the iOS `App.swift` boot path.
  - Drop Firebase Analytics — remove all event-logging call sites or stub them.
- This is the most disruptive phase. Android will be unbuildable for hours-to-days inside this phase. **Cut a tag before starting** so you can diff later.
- Output: Android app launches, navigates, shows data, crashes/perf reach Sentry. No functional regressions verified manually screen-by-screen.

### Phase 5 — `:domain` to KMP + feature modules to KMP (~4–5 weeks)
**Inherited from Phase 2 deferral:** before any feature work, migrate `:domain`
to a KMP module (apply `gamedeals.kmp.library` convention), wire Room KMP
(`RoomDatabase.Builder` KMP API + `expect` driver factory), and move Room
entities/DAOs/database to commonMain. Paging migrates concurrently:
`androidx.paging:paging-runtime` → `androidx.paging:paging-common`
(multiplatform-capable). `room-paging` is Android-only — affected DAO methods
either get `expect/actual` shape or stay androidMain.

Then feature modules. Order: home → search → game → store → giveaways → deal → webview. Each move:
- Migrate sources from `src/main/java` to `src/commonMain/kotlin`.
- Migrate `R.string.*` / `R.drawable.*` to `compose.resources` (`Res.string.*`, `Res.drawable.*`).
- Migrate `androidx.lifecycle.ViewModel` import to `androidx.lifecycle.viewmodel` multiplatform.
- Migrate `androidx.navigation.*` imports to navigation-multiplatform.
- Replace `LocalContext` usages — most are trivial (toast/snackbar). Use Compose Multiplatform alternatives or `expect/actual` as needed.
- Coil 2 → Coil 3 API changes at every `AsyncImage` / `ImageLoader` call site.
- WebView module gets `expect class WebViewHost` with Android (`android.webkit.WebView`) actual and iOS (`WKWebView` via `UIKitView`) actual.
- Output: each feature compiles for both targets. After each feature, manually run the Android app on that screen and verify behavior.

### Phase 6 — iOS app + first run (~1–2 weeks)
- Create `iosApp/` Xcode project.
- Configure Gradle to publish XCFramework via SPM.
- SwiftUI `App` struct hosts `ComposeUIViewControllerRepresentable` that calls into a `MainViewControllerKt.MainViewController()` exported from `:app` (or a new `:iosApp` module on the Kotlin side).
- Validate: simulator launches, navigation works, network calls succeed, Room reads/writes succeed.
- Output: first running iOS build. Bugs expected.

### Phase 7 — Long tail (open-ended)
- iOS-specific bugs (Dispatchers.Main, coroutine context, navigation animations, gesture-back).
- WebView polish on iOS.
- Sentry symbol upload for iOS.
- Resource artifacts (icons, splash, info.plist).
- iOS unit tests (per K, revisit at session end).

## Critical files to modify

### Build / convention
- `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidLibraryConventionPlugin.kt`
- `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidLibraryComposeConventionPlugin.kt`
- `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidFeatureConventionPlugin.kt`
- `build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidKspConventionPlugin.kt`
- `gradle/libs.versions.toml`
- `settings.gradle.kts`

### Domain / data
- `domain/src/main/java/pm/bam/gamedeals/domain/db/DomainDatabase.kt`
- `domain/src/main/java/pm/bam/gamedeals/domain/db/converters/*.kt`
- `domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt`
- `common/src/main/java/pm/bam/gamedeals/common/datetime/*.kt`
- `common/src/main/java/pm/bam/gamedeals/common/storage/SettingStorage.kt`

### Remote
- `remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt`
- `remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/api/*.kt`
- `remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/di/RemoteModule.kt`
- `remote/gamerpower/src/main/java/pm/bam/gamedeals/remote/gamerpower/GamerPowerSourceImpl.kt`
- All `RemoteExceptionTransformer` usages

### App / DI / observability
- `app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt`
- `app/src/main/java/pm/bam/gamedeals/MainActivity.kt`
- `app/src/main/java/pm/bam/gamedeals/di/AppModule.kt`
- `base/src/main/java/pm/bam/gamedeals/base/LoggingBaseActivity.kt` (deleted)
- `logging/src/main/java/pm/bam/gamedeals/logging/**/*.kt`
- `app/google-services.json` (delete after Firebase removal)

### Feature modules (each touched in Phase 5)
- `feature/home/**`
- `feature/game/**`
- `feature/search/**`
- `feature/store/**`
- `feature/giveaways/**`
- `feature/deal/**`
- `feature/webview/src/main/java/pm/bam/gamedeals/feature/webview/ui/WebView.kt` (rewrite as `expect/actual`)

### Existing utilities to reuse
- `Clock` interface in `:common` — survives the datetime swap, used for testability per `concurrency.md`.
- `Destination` sealed interface in `common/src/main/java/pm/bam/gamedeals/common/navigation/Destination.kt` — type-safe `@Serializable` destinations port directly to navigation-multiplatform.
- `RemoteHttpException` sealed hierarchy and `.catch` → UI state pattern from `errors.md` — interface stays, only the upstream source changes.
- `CachedResource` TTL pattern from `data.md` — pure Kotlin, ports as-is.
- Sealed screen state + `StateFlow` + `WhileSubscribed(5000)` pattern from `ui-state.md` — KMP-compatible already.
- Custom serializers (`LocalDateSerializer` etc.) — already abstracted from `java.time`; small adjustment for `kotlinx-datetime`.

## Regression-evidence section

Solo + huge migration + 3–6 months + Android can be broken = high probability of silent regressions. The mitigations below are non-negotiable parts of the plan.

### Tag-and-diff strategy

- Cut a git tag `pre-kmp-migration` at HEAD before Phase 0 begins.
- Cut a sub-tag at the start of each phase (`pre-kmp-phase-2`, etc.). These are recovery points and diff anchors.
- At end of each phase, manually walk every screen on Android and compare to the tagged behavior. Take screenshots into `.claude/notes/kmp-migration/phase-N/screenshots/`.

### "Golden journeys" smoke list

A fixed list of journeys to manually run after every phase before considering the phase complete. Driven from the existing `app/src/androidTest/.../HomeToStoreToDealJourneyTest` plus a few more. Document in `.claude/notes/kmp-migration/golden-journeys.md`:

1. Cold start → home loads top deals → tap deal → bottom sheet opens → tap "open" → WebView loads.
2. Search "halo" → results render → tap result → game detail loads → deal list loads.
3. Stores list → tap a store → deal list filters → tap deal.
4. Giveaways tab → list loads → tap → detail.
5. Settings (if any) — pref changes persist across cold start.
6. Rotate device on each screen — state preserved.
7. Airplane mode → error states render correctly per `errors.md` taxonomy.
8. Cold start with stale cache → `CachedResource` TTL behavior visible.

Each journey: PASS/FAIL with date and phase. A regression is treated as a phase blocker, not a follow-up.

### Test suite as scaffolding

The existing 22 unit tests + 13 instrumented Android tests stay in place during migration. They will break temporarily during Phase 4 (DI swap). Treat each red test in those phases as a forcing function — *do not* skip or delete a test to unblock a phase. If a test no longer expresses what we care about, write its replacement before deleting the original.

### "Behavior diff" reviews

At the end of Phase 4 and Phase 5, run `git diff pre-kmp-phase-N..HEAD -- '*.kt'` and review with the question "did any business logic change here that wasn't supposed to?" The point is to catch accidental semantic edits introduced while doing what was supposed to be a pure mechanical port.

### Sentry as ground truth

After Phase 4, every Android build runs through Sentry. Any uncaught exception in a manually-run journey shows up in Sentry. Treat the Sentry inbox after a manual smoke pass as part of the phase signoff.

### Performance baseline

Before Phase 0: capture cold-start time and a frame-rate sample for the home screen on a real Android device. After Phase 5: re-capture. A regression > 30% in either is a phase blocker.

## Verification

After the full migration, "done" means all of:

- `./gradlew :app:assembleDebug` succeeds (uses local JBR 21 per the project memory).
- `./gradlew test` passes — unit tests rebuilt against Koin/Ktor/Room-KMP/Sentry.
- `./gradlew connectedAndroidTest` passes — instrumented tests rebuilt against the new stack (per K, this stays Android-only initially).
- Open `iosApp/iosApp.xcodeproj` in Xcode, run on iPhone Simulator: app launches, all 7 features reachable, network + DB working, no crashes in a 5-minute exploration session.
- All 8 golden journeys PASS on Android against the migrated build.
- Sentry inbox shows no new uncaught exceptions across a smoke pass.
- Cold-start regression < 30% vs. pre-migration baseline.
- No reference to `androidx.hilt.*`, `dagger.*`, `retrofit2.*`, `com.google.firebase.*`, `coil.compose.*` (Coil 2), or `java.time.*` remains in any `commonMain` source.
- README / `AGENTS.md` updated to reflect KMP build instructions and the new module shape.

## What's deliberately out of scope for this plan

- iOS unit/UI test suite (deferred per K; revisit end of session).
- iOS CI (deferred — local-build only).
- App Store distribution (no developer account yet; out of scope).
- R8 / minification.
- Cupertino theming / per-platform UI components (J1 = Material3 everywhere accepted).
- Product analytics replacement (I1 = dropped entirely).
- WebView feature parity edge cases (cookie sync, file download, JS bridges) — implemented to current Android behavior, no expansion.
