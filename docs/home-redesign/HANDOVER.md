# Handover — Home redesign + bottom-nav shell + ITAD account (epic #219)

Living handover for epic **#219**. Updated at the end of each phase so another agent can resume cold.

- **Epic:** #219 · **Project board:** "Home Redesign & ITAD Account" (`https://github.com/users/Mithrandir21/projects/2`)
- **Tickets:** #220–#240 (phase-grouped task-list on the epic)
- **Plan (full design):** `~/.claude/plans/since-the-epic-was-peaceful-mountain.md`
- **Prior epic this mirrors:** #205 ITAD migration — see `docs/deal-source-migration/HANDOVER.md` for the phasing/migration discipline.

## Locked product decisions (do not relitigate — see plan/epic for rationale)
- Bottom `NavigationBar`: **Home · Deals · Giveaways · Account**. Search = `TopAppBar` action; Settings + "Browse by store" = top-bar overflow.
- Home = curated feed: stat cards → Featured hero grid → Trending → Most Waitlisted (30d) → Most Collected (30d) → New Releases (IGDB) → Bundles → Giveaways.
- Deals tab = full all-stores `/deals/v2` browser. Account = ITAD **OAuth (auth-code + PKCE), read+write**.
- **Local Favourites is replaced by the ITAD Waitlist** (login-gated heart; drop `FavouriteGame`, Room v7→v8).
- Row tap → existing `DealBottomSheet`. Dropped: "New in Subscriptions", "Misc. Deals".

## Build / verify
```
export JAVA_HOME=/opt/android-studio/jbr      # JDK 21 (Android Studio JBR)
./gradlew :app:assembleDebug --continue        # full release build fails only at signing; use assembleDebug
./gradlew <module>:testAndroidHostTest --continue   # always --continue (one module failure masks later ones)
```
- iOS: this is a **Linux** dev box — Kotlin/Native iOS targets generally don't build here. `commonMain` is validated via the Android compilation; **iosMain** edits (e.g. `MainViewController.kt`) need a macOS compile check (`./gradlew :iosApp:compileKotlinIosSimulatorArm64`). Flag iosMain changes for review.
- Pre-existing unrelated test failure on `dev`: `:remote:igdb` `IgdbSourceImplTest.fetchGameDetailsByTitle_…normalized_title…`.

## Phase status
| Phase | Tickets | Status |
|---|---|---|
| 0 — Seams & scaffold | #220–#223 | ✅ DONE (branch `feat/phase0-home-redesign-scaffold`) |
| 1 — App shell wired | #224–#225 | ⬜ next |
| 2 — OAuth + Account | #226–#229 | ⬜ |
| 3 — Favourites→Waitlist + Room v7→v8 | #230–#232 | ⬜ |
| 4 — Deals tab | #233–#234 | ⬜ |
| 5 — Curated Home feed | #235–#237 | ⬜ |
| 6 — Cleanup & hardening | #238–#240 | ⬜ |

---

## Phase 0 — DONE
No user-visible change; everything compiles and tests pass (Android `:app:assembleDebug` green; `:domain` 44 tests, `:feature:home` 24 tests, 0 failures). Each piece is a stable seam for later phases.

**#220 — Destinations + `navigateTopLevel()`**
- `common/.../navigation/Destination.kt`: added `Destination.Deals` and `Destination.Account` (top-level tab destinations).
- `app/.../navigation/Navigation.kt`: added `NavigationActions.navigateTopLevel(destination)` (the tab back-stack recipe: `popUpTo(start){saveState}; launchSingleTop; restoreState`); `navigateHome()` now delegates to it. iOS has no `NavigationActions` equivalent yet — Phase 1.1 wires the iOS side.

**#221 — App-shell composables (not yet hosting)**
- `common/ui/.../shell/TopLevelDestination.kt`: enum of the 4 tabs (each → `Destination` + `ImageVector` + label `StringResource`).
- `common/ui/.../shell/AppShellScaffold.kt`: `GameDealsAppShell(selectedTab, onSelectTab, onSearch, onOpenSettings, onBrowseStores, content)` — M3 `Scaffold` + `NavigationBar` + `TopAppBar` (Search action + overflow). **Deliberately navigation-agnostic** (navigation-compose is androidMain-only in `:common:ui`; the host computes `selectedTab` from the route and passes callbacks).
- `common/ui/.../composeResources/values/strings.xml`: added `app_shell_*` strings (title, search, more, overflow settings/stores, 4 tab labels).
- Note: `:common:ui` commonMain gets M3 + `materialIconsExtended` + resources from the `gamedeals.kmp.library.compose` convention plugin (that's how `DealBottomSheet` uses M3 in commonMain).

**#222 — Account/stats domain seams + repositories + AuthTokenStore (stubs)**
- Models (`domain/.../models/`): `AuthState` (sealed: LoggedOut / LoggedIn(username)), `ItadUser`, `WaitlistEntry`, `CollectionEntry`, `RankedGame`.
- Source seams (`domain/.../source/`): `ItadAccountSource` (user/waitlist/collection r+w), `StatsSource` (most-waitlisted/collected/popular). **Interfaces only — no impl bound yet** (live impls land in `:remote:itad` in Phases 2.3/5.1).
- `domain/.../auth/AuthTokenStore.kt`: interface + **working** `AuthTokenStoreImpl` (Storage-backed, mirrors `RegionRepositoryImpl`'s lazily-seeded `MutableStateFlow`; persists a `StoredAuthToken` JSON blob; derives `AuthState`). Tokens are **unencrypted** → Phase 6.2 (#239).
- Repositories (`domain/.../repositories/`): `AccountRepository` (real — wraps `AuthTokenStore` for auth state + logout); `WaitlistRepository`, `CollectionRepository`, `StatsRepository` (**STUBS** — empty flows / no-op / empty lists until their sources land). `WaitlistRepository` mirrors the old `FavouritesRepository` shape (`observeWaitlistIds`/`observeIsWaitlisted`/`toggleWaitlist`) so Phase 3 swaps call sites cleanly.
- `domain/.../di/DomainModule.kt`: registered `AuthTokenStore` (`get(SETTINGS_QUALIFIER)` Storage) + the 4 repositories.

**#223 — OAuth secret plumbing (no runtime use yet)**
- `app/build.gradle.kts`: `itadOauthClientId` from `local.properties (itadOauthClientId)` / env `ITAD_OAUTH_CLIENT_ID` → `buildConfigField ITAD_OAUTH_CLIENT_ID`.
- `remote/itad/.../auth/ItadCredentials.kt`: added `oauthClientId` + `redirectUri` (default const `DEFAULT_ITAD_REDIRECT_URI = "pm.bam.gamedeals://oauth/itad"`).
- Android `GameDealsApplication.kt` and iOS `MainViewController.kt`: pass the client id (`BuildConfig.ITAD_OAUTH_CLIENT_ID` / `infoPlistString("ITADOAuthClientId")`).
- iOS `Info.plist`: `ITADOAuthClientId = $(ITAD_OAUTH_CLIENT_ID)`; `Secrets.xcconfig.template`: added `ITAD_API_KEY` + `ITAD_OAUTH_CLIENT_ID` rows. **Real values must be set in the gitignored `Secrets.xcconfig` / `local.properties` before Phase 2 runtime use.**

---

## Next up — Phase 1 (app shell wired; LARGE/RISKY)
**#224 (1.1):** Wrap both NavHosts in `GameDealsAppShell`. Anchors:
- Android: `app/.../navigation/NavGraph.kt` (build `navController`, wrap the `NavHost` in `GameDealsAppShell`; compute `selectedTab` from `navController.currentBackStackEntryAsState()` route → `TopLevelDestination`; pass `onSelectTab = { navActions.navigateTopLevel(it.destination) }`, `onSearch`, `onOpenSettings`, `onBrowseStores`). Add placeholder `Destination.Deals`/`Account` `composable<>` screens. **Remove the Home FABs** (`HomeScreen.kt:303-343`) and drop the now-unused Home FAB callbacks; Search moves to the top bar.
- iOS: mirror in `iosApp/.../MainViewController.kt`'s `AppNavHost` (it has no `NavigationActions` — either add an equivalent helper or inline the same `navigate{}` block).
- App stays green: Deals/Account render empty placeholders until Phases 4/2.

**#225 (1.2):** Hide the bar on detail routes (Store/Game/WebView/Search/BundleDetail) via current-route matching; verify tab state restoration.

**Heads-up for Phase 3:** DB is at **v7**; dropping `FavouriteGame` is `MIGRATION_7_8` + regen `domain/schemas/.../8.json` (`./gradlew :domain:kspAndroidMain`) + register in `DOMAIN_MIGRATIONS` — the build-gating `DomainDatabaseMigrationTest` enforces this.
