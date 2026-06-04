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
| 0 — Seams & scaffold | #220–#223 | ✅ DONE (merged to `dev`) |
| 1 — App shell wired | #224–#225 | ✅ DONE (on `dev`) |
| 2 — OAuth + Account | #226–#229 | 🔄 in progress — 2.1 (#226) DONE; 2.2–2.4 remain |
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

## Phase 1 — DONE
> From Phase 1 onward, work is committed **directly to `dev`** (no per-phase branches — user decision).

**#224 (1.1) + #225 (1.2)** — the app shell is now live on **both** platforms.
- `GameDealsAppShell` (`common/ui/.../shell/AppShellScaffold.kt`) wraps the NavHost in Android `NavGraph.kt` and iOS `MainViewController.AppNavHost`. It shows the bottom `NavigationBar` (4 tabs) on top-level routes and a `TopAppBar` (Search action + overflow → Settings) on Home/Deals/Account; **no chrome on detail routes** (they keep their own `Scaffold`/`TopAppBar`). `selectedTab` is derived from `currentBackStackEntryAsState()` via `NavDestination.hasRoute`; tab taps go through `navigateTopLevel` (Android `NavigationActions.navigateTopLevel`; iOS inline) using `popUpTo(start){saveState}; launchSingleTop; restoreState`.
- `contentWindowInsets = 0` on the shell **and** on Home's inner `Scaffold` so insets aren't doubled.
- **Home FABs removed.** Search → shell top bar; Settings → shell overflow; error-retry stays on the existing snackbar. The LOADING spinner lived in the main FAB and is gone — the empty `LazyColumn` shows the "Loading" section header instead (instrumented `HomeScreenTest.loadingState` re-pointed to `home_screen_loading_label`).
- Signatures trimmed: `HomeScreen`/`HomeRoute`/`homeScreen()` dropped `onSearch`/`onViewSettings` (and `goToSearch`/`goToSettings`). Home "View All Giveaways" and the Giveaways tab both use `navigateTopLevel(Destination.Giveaways)`.
- **Placeholder tabs**: `PlaceholderTabScreen` (`common/ui/.../shell/`) registered for `Destination.Deals`/`Account` on both platforms — replaced by `:feature:account` (#229) and `:feature:deals` (#234).

**Known interims / follow-ups (not blocking):**
- The **Giveaways tab keeps its own top bar** (incl. a now-redundant back button); the shell passes `showTopBar = false` on that route to avoid a double bar, so **Search isn't reachable while on the Giveaways tab**. Unifying Giveaways into the shell top bar is a tracked follow-up (fold into Phase 5 or a small task).
- The `onBrowseStores` overflow item is wired but passed `null` (no all-stores destination yet) so it's hidden for now.
- Inset/visual polish + tab back-stack behaviour were reasoned about but **not visually verified** (Linux box can't run the emulator) — confirm on a device.

Verified: `:app:assembleDebug` green; `:feature:home:compileAndroidDeviceTest` green; full `testAndroidHostTest` green **except** the known pre-existing `:remote:igdb` `IgdbSourceImplTest` failure.

---

## Phase 2 — IN PROGRESS (ITAD OAuth + Account tab; LARGE/RISKY)
Seams + secret plumbing exist (Phase 0). **Fill real values before runtime/login**: `local.properties itadOauthClientId` and `Secrets.xcconfig ITAD_OAUTH_CLIENT_ID` (+ register redirect `pm.bam.gamedeals://oauth/itad` with ITAD).

**#226 (2.1) — DONE (on `dev`).** All in `remote/itad/.../auth/oauth/` + `logic/`:
- `Pkce.kt` — pure-Kotlin SHA-256 (validated against the RFC 7636 + FIPS "abc" vectors) + base64url(no-pad); `generatePkce()` / `randomState()` / `codeChallenge()`. *(verifier/state use `Random.Default` — CSPRNG upgrade tracked with token encryption in #239.)*
- `ItadOAuthConfig` (authorize/token URLs on `isthereanydeal.com`, scopes), `RemoteItadTokenResponse`, `ItadOAuthClient` (`buildAuthorizeUrl` + `/oauth/token` exchange/refresh via `submitForm`), `ItadTokenProvider` (bridges `AuthTokenStore` + refresh → persists/clears).
- `logic/ItadAuthHttpClient.kt`: `itadOAuthHttpClient` (plain, no key, absolute URLs) + `itadAuthHttpClient` (bearer client, `Auth.bearer` + `refreshTokens`, base = api host). Registered in `itadNetworkModule` under `ITAD_OAUTH_QUALIFIER` / `ITAD_AUTH_QUALIFIER` (no new Koin module to register). `AuthTokenStore` gained `getUsername()`. Added `ktor-client-auth`.
- Tests (commonTest, MockEngine): `PkceTest`, `ItadOAuthClientTest`, `ItadTokenProviderTest` — all green. **OAuth endpoint URLs are documented assumptions** — confirm against ITAD docs during the 2.4 live smoke test.

**Remaining:**
- **#227 (2.2):** `AuthBrowserLauncher` expect/actual — Android Custom Tabs + redirect activity (`AndroidManifest` intent-filter for `pm.bam.gamedeals://oauth`), iOS `ASWebAuthenticationSession` + `CFBundleURLSchemes`. **The redirect-capture Activity likely belongs in `:app`** (manifest owns the scheme); the launcher contract can live in `:remote:itad` androidMain/iosMain. iOS actual can't be compiled on the Linux box — flag for macOS.
- **#228 (2.3):** `ItadUserApi`/`ItadWaitlistApi`/`ItadCollectionApi` (use `get(ITAD_AUTH_QUALIFIER)` bearer client) + `ItadAccountSourceImpl`; replace the Phase 0 stub `AccountRepository`/`WaitlistRepository`/`CollectionRepository` impls with real ones (the login flow: exchange code → call `/user/info/v2` → `AuthTokenStore.saveTokens(..., username)`).
- **#229 (2.4):** `:feature:account` module — logged-out CTA → `ItadOAuthClient.buildAuthorizeUrl` → `AuthBrowserLauncher` → exchange; logged-in profile + stat cards + waitlist + collection (add/remove). Replace the Account `PlaceholderTabScreen`.

**Heads-up for Phase 3:** DB is at **v7**; dropping `FavouriteGame` is `MIGRATION_7_8` + regen `domain/schemas/.../8.json` (`./gradlew :domain:kspAndroidMain`) + register in `DOMAIN_MIGRATIONS` — the build-gating `DomainDatabaseMigrationTest` enforces this.
