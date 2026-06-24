# Phase 4 — Hilt → Koin + Firebase → Sentry

**Branch:** `feature/kmp-migration-phase-4-di-observability`
**Sub-commits:**
- 4.1 — Sentry adapter plumbing alongside Hilt (commit `fe9e17e`)
- 4.2 — Hilt → Koin in `:app` + `:common` + `:domain` + `:logging` + `:base` (commit `84d6ac9`)
- 4.3 — Hilt → Koin in `:remote*` (commit `9f9b4ec`)
- 4.4 — Hilt → Koin in feature modules; runtime restored (commit `47f8b62`)
- (fix) — `DealsApi.getDeal` 404 from double URL-encoding (commit `012d27b`)
- 4.6 — Firebase out, catalog trimmed, dead stubs deleted (commit `7878877`)

## What was done

### Tech swaps
- **DI:** Hilt → Koin 4.0.0 across every module. `@HiltAndroidApp` → `startKoin { androidContext(...); modules(...) }` in `GameDealsApplication`. `@HiltViewModel` + `hiltViewModel()` → Koin `viewModel { ... }` + `koinViewModel()` in the 5 navigated feature ViewModels. Per-module Koin modules (`loggingModule`, `commonModule`, `domainModule`, `remoteModule`, `cheapsharkRemoteModule`, `cheapsharkNetworkModule`, `gamerpowerRemoteModule`, `gamerpowerNetworkModule`, `appModule`, plus 5 feature `<feature>Module`s) — 14 modules registered at startup.
- **Observability:** Firebase Crashlytics / Performance → Sentry KMP 0.13.0 via a `SentryLoggingListener` that implements `:logging`'s existing `LoggingInterface`. ERROR/FATAL → `Sentry.captureException`/`captureMessage`; lower levels → breadcrumbs. `Sentry.init { ... }` is gated on a non-empty DSN constant — currently `""`, so the SDK no-ops until a project is provisioned.
- **Firebase Analytics:** dropped (production callers were one unused field on `MainActivity` + the provider that fed it; both deleted in 4.2).
- **Build files:** `firebase.crashlytics`/`google-services` plugins out of `:app`; `hilt`/`firebase-*` apply-false aliases out of root; orphan Hilt deps out of `:testing`.
- **Catalog:** Firebase entries (versions, libraries, plugins) dropped entirely. Hilt entries kept — still consumed by `:app`'s androidTest source pending a Phase 4.7 rewrite.

### Module shape after Phase 4
- `:app` — Koin bootstrap in `GameDealsApplication.onCreate`. `MainActivity` is a plain `ComponentActivity` (no `LoggingBaseActivity` ancestor; no `@AndroidEntryPoint`). `appModule` provides `Clock`, the Coil `ImageLoader`, and an internal Coil `Logger` adapter. `androidTest` source still uses Hilt — deferred.
- `:logging` — `loggingModule` registers `LoggerImpl(setOf(SimpleLoggingListener(), SentryLoggingListener()))`. No KSP, no Hilt. Sentry KMP added as `implementation`.
- `:common`, `:domain`, `:remote`, `:remote:cheapshark`, `:remote:gamerpower` — Koin modules, all `@Inject`/`@HiltViewModel`/`@Module`/`@Provides`/`@Settings`/`@Domain`/`@CheapShark`/`@GamerPower` annotations stripped. Two `named()` qualifiers remain — `named("settings")` for SharedPreferences + Storage in `:common`, `named("cheapshark")`/`named("gamerpower")` for the two `HttpClient` bindings.
- `:base` — empty module (LoggingBaseActivity + LoggingBaseFragment deleted; no production source). Pending a follow-up that either drops the module or repurposes it.
- `:testing` — Koin-aware (in spirit); had no Hilt source-side usage, just orphan deps that 4.6 removed.
- Feature modules — one `di/<Feature>Module.kt` each (5 files), `viewModel { ... }` blocks with explicit constructor wiring (`get(), get(), …`); `SavedStateHandle` is auto-resolved by Koin's `viewModel` scope where needed (`GameViewModel`, `StoreViewModel`).

### Notable decisions
- **`AndroidFeatureConventionPlugin` rewritten.** The convention plugin's universal Hilt + KSP block became a Koin block: `koin-core`, `koin-android`, `koin-androidx-compose`, `koin-compose-viewmodel`. Per-feature `build.gradle.kts` files lost their inherited Hilt deps automatically.
- **Per-module Koin modules over a single mega-module.** Each Gradle module owns its own Koin module file; `:app` aggregates them in the `startKoin { modules(...) }` call. Mirrors the Hilt @Module-per-Gradle-module shape and keeps each module self-contained for the Phase 5/6 KMP migration.
- **Sentry DSN deferred.** `SENTRY_DSN` constant in `GameDealsApplication` is `""` until a project is provisioned. `initSentry()` early-returns; `Sentry.isEnabled()` returns false; `SentryLoggingListener.isEnabled()` returns false; the listener is wired but no events flow. Provisioning a DSN is a one-line change.
- **`LoggingBaseActivity` + `LoggingBaseFragment` deleted.** Per the plan. Single-activity Compose app — `LoggingBaseActivity`'s ~30 lifecycle log calls were the entire reason for the inheritance, and they were debug-noise rather than load-bearing instrumentation.
- **Firebase Analytics call sites already gone after 4.2.** PLAN.md scheduled a separate 4.5 audit step but the only call sites in production code were one injected-but-unused field on `MainActivity` and its provider — both fell out of the `MainActivity` rewrite. Folded 4.5 into 4.6 cleanup.
- **androidTest rewrite deferred initially to a follow-up "Phase 4 tidy" PR.** `:app/src/androidTest/` was not just runtime-broken but also compile-broken (the `Test*NetworkModule.kt` files still used Retrofit's `retrofit.create(DealsApi::class.java)` against API classes that became Ktor wrappers in Phase 3, plus referenced the `@CheapShark`/`@GamerPower`/`@Domain` qualifiers that were deleted in Phase 4.2/4.3, plus pulled in mockwebserver:5.2.1 which conflicts with Ktor's transitive okhttp 4.12.0). Resolved in the tidy PR by rewriting the androidTest infra to Koin + Ktor MockEngine — see commits below.

### Mid-stream bug fix — DealsApi 404 from double URL-encoding
After 4.4 restored runtime, the user's smoke test caught a 404 when tapping any deal card. CheapShark's `/deals` list endpoint returns dealIDs already percent-encoded (e.g. `…%3D`); the original Retrofit code used `@Query("id", encoded = true)` to pass the value through verbatim. Phase 3's `DealsApi.getDeal` port to Ktor's `parameter("id", id)` lost the `encoded = true` semantic — Ktor re-encoded `%` → `%25`, producing `…%253D` on the wire, and the lookup 404'd. Fix: `url { encodedParameters.append("id", id) }`. Regression test added at `remote/cheapshark/src/androidUnitTest/.../api/DealsApiTest.kt`. Captured as `L-2026-05-03-03`.

### Other gotchas worth surfacing
- **Room KSP `[MissingType]` after dropping Hilt from `:domain`.** `androidx.hilt:hilt-navigation-compose` had been transitively bringing `androidx.compose.runtime:runtime` to `:domain`'s compile classpath. Room entities use `@Immutable` for Compose stability hints; without Compose runtime, KSP can't resolve the annotation and fails with a misleading "references a type that is not present" pointing at the entity, not at the missing annotation. Fix: explicit `implementation(libs.androidx.compose.runtime)` on `:domain`. Captured as `L-2026-05-03-04`.
- **Sentry KMP `Sentry.init(context, ...)` is deprecated in 0.13.0** in favour of `Sentry.init { options -> ... }`. Both signatures exist on Android; the deprecated one fires a compile warning. Switched to the no-context overload.

## Build verification

| Task | Result |
|---|---|
| `:app:assembleDebug` (whole project) | ✅ |
| `./gradlew test` (whole project unit tests) | ✅ |
| Smoke test on device — Home, Stores, Search, Game details, Deal bottom sheet, Giveaways, WebView | ✅ (after 012d27b) |
| iOS sim compile | not exercised this phase — no iOS-side changes |

## Lessons captured

- `L-2026-05-03-03` · Ktor `parameter()` always encodes — use `encodedParameters` for already-encoded values
- `L-2026-05-03-04` · Removing a Hilt module (esp. `hilt-navigation-compose`) silently strips Compose runtime from KSP classpath

Both confirmed; no supersessions.

## Phase 4 tidy PR

Bundled three deferred items into one follow-up branch (`feature/kmp-migration-phase-4-tidy`):

- **androidTest rewrite to Koin + Ktor MockEngine.** New `KoinTestRunner` swaps in a `TestGameDealsApplication` that loads the production Koin graph with two test-override modules layered on top: `testNetworkOverridesModule` redefines the `cheapshark`/`gamerpower` `HttpClient` bindings to use `MockEngine` backed by `FixtureRequestHandler` (a Ktor port of the old `FixtureMockDispatcher`); `testDatabaseOverridesModule` swaps the production Room DB for an in-memory one. `HomeToStoreToDealJourneyTest` body is unchanged from a Compose-assertion perspective — only the test-infra wiring around it changed. JSON fixtures preserved verbatim under `androidTest/assets/fixtures/`.
- **`:base` module deleted.** No production sources after `LoggingBase*.kt` deletion. Removed from `settings.gradle.kts`; `implementation(project(":base"))` removed from `:app/build.gradle.kts`; `base/` directory deleted.
- **`google-services.json` removed** from `:app/`.

After this, all Hilt catalog entries (`hilt-core`/`hilt-plugin`/`hilt-compiler`/`hilt-navigation`/`hilt-testing` versions and 5 Hilt libraries) were dropped — nothing in the project references them.

## Remaining follow-ups

- **Sentry DSN provisioning.** When a Sentry project is set up, set `SENTRY_DSN` in `GameDealsApplication`.

## Next phase

Phase 5 — `:domain` → KMP + Room KMP + Paging-multiplatform + feature modules → KMP. ~4–5 weeks. Cut tag `kmp-pre-phase-5` on `feature/kmp-migration` before starting.
