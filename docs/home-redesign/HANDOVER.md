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
| 2 — OAuth + Account | #226–#229 | ✅ DONE (on `dev`) — ⚠️ iOS compile + live login unverified |
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

**#228 (2.3) — DONE (on `dev`).**
- Bearer-client user APIs (`get(ITAD_AUTH_QUALIFIER)`): `ItadUserApi` (`/user/info/v2`), `ItadWaitlistApi` + `ItadCollectionApi` (GET list of `obj.game`; PUT/DELETE take a **JSON array of game-id strings**, 204 — verified against the ITAD OpenAPI). DTO `RemoteItadUser`; waitlist/collection items reuse `RemoteItadSearchGame`. Mappers in `mappers/ItadAccountMappers.kt`.
- `ItadAccountSourceImpl` implements the `ItadAccountSource` seam (log→transform→getOrThrow like `ItadSourceImpl`); add/remove send a one-element id array. Registered in `itadRemoteModule`; APIs in `itadNetworkModule`.
- **Real `WaitlistRepository`/`CollectionRepository`** (replaced the Phase 0 stubs): a `MutableStateFlow` id cache over the source; `getWaitlist()/getCollection()` refresh it, `toggle*` does an optimistic local update around the remote add/remove, **all writes login-gated** (no-op / empty when logged out, checked via `AuthTokenStore.getAccessToken()`). DI now injects `(ItadAccountSource, AuthTokenStore)`.
- Tests (all green): `ItadAccountSourceImplTest` (6), `WaitlistRepositoryTest` (5), `CollectionRepositoryTest` (3). `:app:assembleDebug` green. **Endpoint shapes match the OpenAPI but the live request/response wasn't run (OAuth-gated) — confirm at the 2.4 smoke test.**

**#227 (2.2) — DONE (on `dev`; Android verified, iOS unverified).**
- Contract: `auth/oauth/AuthBrowserLauncher.kt` (commonMain) — `interface AuthBrowserLauncher.authorize(authorizeUrl, redirectScheme): AuthRedirectResult` (`Success(code,state)` / `Cancelled` / `Failed`). Bound per-platform (interface + DI, not expect/actual).
- **Android** (androidMain): `AndroidAuthBrowserLauncher` launches a plain `ACTION_VIEW` browser intent (no Custom Tabs dep) and awaits `AuthRedirectBus`; the redirect `Activity` lives in **`:app`** (`pm.bam.gamedeals.oauth.OAuthRedirectActivity`) with the manifest `<intent-filter>` (`scheme=pm.bam.gamedeals`, `host=oauth`) → calls `AuthRedirectBus.deliver(uri)`. `itadAndroidModule` binds it (`androidContext()`); registered in `GameDealsApplication`. Added `koin-android` to `:remote:itad` androidMain. **Compiles + assembles.**
- **iOS** (iosMain): `IosAuthBrowserLauncher` (`ASWebAuthenticationSession` + a presentation-context provider); `itadIosModule` registered in `MainViewController`; `CFBundleURLTypes` (scheme `pm.bam.gamedeals`) added to `Info.plist`. **⚠️ NOT compiled (Linux box) — verify with `:iosApp:compileKotlinIosSimulatorArm64` on macOS.** Uncertain K/N bits flagged in the file: `keyWindow` anchor (iPad/scene; cf. #144), cancel error-code `== 1L`, `queryItems` cast.
- **Not yet consumed** — the login orchestration that calls `authorize()` lands in 2.4. Cancel/dismiss UX (no redirect) is best-effort; verify on device.

**#229 (2.4) — DONE (on `dev`; Android verified, iOS/live unverified). PHASE 2 COMPLETE.**
- Login orchestration behind a **domain seam**: `ItadLoginSource.login(): ItadUser?` (domain) → `ItadLoginSourceImpl` (`:remote:itad`) does PKCE → `buildAuthorizeUrl` → `AuthBrowserLauncher.authorize` → `exchangeCodeForToken` → provisional `saveTokens(username="")` → `getUserInfo` → re-`saveTokens(username)`; cancel→null, state-mismatch/failure→throw. `AccountRepository` gained `login()` (delegates to the seam); both bound in DI.
- New **`:feature:account`** module: `AccountViewModel` (observes auth state → loads waitlist+collection; `onLogin`/`onLogout`) + `AccountScreen` (M3: logged-out CTA; logged-in profile + Waitlisted/Collected stat cards + waitlist/collection lists, row→game). Wired into settings.gradle / root Kover / `:app` / `:iosApp` deps / both Koin module lists / both NavHosts (replaced the Account `PlaceholderTabScreen`).
- Tests (green): `ItadLoginSourceImplTest` (3), `AccountViewModelTest` (4). `:app:assembleDebug` green.

**⚠️ Phase 2 verification gaps (need a Mac + the real OAuth client id):**
- iOS Kotlin never compiled here — run `:iosApp:compileKotlinIosSimulatorArm64` on macOS (the `IosAuthBrowserLauncher` is the main risk).
- Live login never run — fill `local.properties itadOauthClientId` / `Secrets.xcconfig ITAD_OAUTH_CLIENT_ID`, register the redirect `pm.bam.gamedeals://oauth/itad` with ITAD, then smoke-test: login → `/user/info` → username; add/remove waitlist; force a 401 → silent refresh; logout. Confirm the `ItadOAuthConfig` URLs + the `obj.game` PUT/DELETE body shapes against the live API.
- "Heat" stat card omitted (no clean ITAD source) — only Waitlisted/Collected shown.

## Next up — Phase 3 (Favourites → Waitlist + Room v7→v8; RISKY)
The waitlist repo now exists (Phase 2.3), so the heart can be repointed.
- **#230 (3.1):** Repoint the heart in `common/ui/.../deal/DealBottomSheet.kt` (`isFavourite`→`isWaitlisted`, `onToggleFavourite`→`onToggleWaitlist`) + its 3 call sites + previews, and the Home/Store/Game/Search VMs → `WaitlistRepository` (login-gated; logged-out tap routes to Account/login via a one-shot event).
- **#231 (3.2):** Drop `FavouriteGame` + Room **v7→v8** (`MIGRATION_7_8` DROP TABLE; bump `DOMAIN_DB_VERSION`; regen `8.json` via `:domain:kspAndroidMain`; register in `DOMAIN_MIGRATIONS`; delete entity/DAO/repo/tests; prune `DomainDatabase`/`DomainModule`). Build-gating `DomainDatabaseMigrationTest` enforces the registration + schema file.
- **#232 (3.3):** Remove/repurpose `:feature:favourites` (fold the waitlist list into Account; delete the module + `Destination.Favourites`; update both NavHosts/Koin/deps/root Kover). Note: Home still has a local "Favourites" strip + "View All" + `favouriteIds`/`favourites` in `HomeViewModel` — repoint or remove those.

**Heads-up for Phase 3:** DB is at **v7**; dropping `FavouriteGame` is `MIGRATION_7_8` + regen `domain/schemas/.../8.json` (`./gradlew :domain:kspAndroidMain`) + register in `DOMAIN_MIGRATIONS` — the build-gating `DomainDatabaseMigrationTest` enforces this.
