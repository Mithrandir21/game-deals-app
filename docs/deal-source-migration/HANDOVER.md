he # Deal-source migration (CheapShark → ITAD) — Handover

**Epic:** [#205](https://github.com/Mithrandir21/game-deals-android-app/issues/205) · **ADR:** [`docs/adr/0001-deal-source-itad.md`](../adr/0001-deal-source-itad.md)
**Status: epic #205 COMPLETE.** ITAD is the live deal source and `:remote:cheapshark` is gone. All phases
are merged to `dev`: 0 ✅, 1 ✅, 2a/2b/2c ✅, 3a price-history chart ✅, 3b regional pricing ✅, 3c bundles ✅,
**4 remove `:remote:cheapshark` ✅**.

This doc captures what's done and the non-obvious things learned this session so you can continue Phase 2
without re-deriving them. Read the "Phase 2 — start here" section first, then the reference sections.

---

## Where we are

Replacing the CheapShark deal source with **IsThereAnyDeal (ITAD)** as a *phased full replace*, behind a
source-neutral `DealsSource` seam so the provider stays swappable (ITAD's ToS allows revocation without
notice — see ADR).

| Phase | What | Status | Issue |
|------|------|--------|-------|
| 0 | Decouple the seam (`DealsSource`, url-in-model, neutral `Store`) + ADR | ✅ merged (`8d8d096`) | #213, #214 |
| 1 | `:remote:itad` module + API key (DealsSource impl) | ✅ merged (`40be334`) | #207 |
| 2a | Domain models → `String` game ids (UUID-ready) + stored `url`/`iconUrl` + Room `v5→v6` migration | ✅ merged to `dev` | (no issue) |
| 2c | Releases → IGDB (`first_release_date`); drop `fetchReleases()` from the seam | ✅ merged to `dev` | (no issue) |
| 2b | Map `ItadSourceImpl` into the migrated models + DI swap; CheapShark-only `Deal` fields → nullable (`v6→v7`) | ✅ merged to `dev` | (no issue) |
| 3a | Real price-history chart on the game screen (Vico) | ✅ merged to `dev` | #208 |
| 3b | Regional pricing — country picker + Settings screen | ✅ merged to `dev` | #212 |
| 3c | Bundles — Home strip + Bundles list + detail screen | ✅ merged to `dev` | (no issue yet) |
| 4 | Remove `:remote:cheapshark` | ✅ merged to `dev` | (no issue yet) |

**ITAD is now the live `DealsSource`** (Phase 2b). `:remote:cheapshark` stays in the tree (its network
singles are still registered but unused) until **Phase 4** removes it.

Related memory note (auto-loaded for Claude sessions): `.claude/.../memory/itad-deal-source-migration.md`.

---

## Phase 2 — start here

Goal (achieved): make ITAD the live source. All three workstreams (2a, 2b, 2c) are **done & merged**.

### 2a. Migrate the domain models to String game ids + Room migration — ✅ DONE (merged to `dev`)
Shipped as the smallest independently-shippable slice (CheapShark stayed the live source, app still works):
- Game ids flipped **`Int → String`** everywhere they are a game identity: `Deal.gameID`,
  `Game.gameID` (PK), `FavouriteGame.gameID` (PK), `DealDetails.GameInfo.gameID`, the typed nav route
  `Destination.Game(gameId: String)`, the shared `DealBottomSheetData.gameId`, every feature screen/VM,
  fixtures and tests. `GamesRepository.findCheapsharkGameIdBySteamAppId` → `findGameIdBySteamAppId`;
  `DealsAction.OpenGame.cheapsharkGameId` → `gameId`.
- `Deal.url` and `Store.iconUrl` promoted from Phase-0 **computed properties** to **stored columns**
  (source-filled by the mappers: CheapShark fills them; ITAD will in 2b).
- Room **`v5 → v6`** migration: `domain/.../db/Migrations.kt` has a manual `Migration(5, 6)` that drops &
  recreates `Deal`/`Game`/`Store`/`FavouriteGame` (caches + **favourites reset on upgrade**, per the user)
  using the exact `createSql` from the regenerated `domain/schemas/.../6.json`. `DomainDatabaseMigrationTest`
  passes (it only checks `6.json` exists + `5→6` is registered).
- **Deliberately deferred to 2b:** CheapShark-only `Deal` fields (`steamRatingPercent`, `dealRating`,
  `releaseDate`, `metacriticScore`, `internalName`, `Store.images`, …) stay **non-null** for now — CheapShark
  still fills them. Making them nullable + UI null-tolerance is 2b's job (its own `v6→v7` migration), when
  ITAD goes live and genuinely can't fill them.

> Note: the original ADR line "favourites preserved by re-resolving title / Steam appID" was superseded by
> a user decision in 2a — **favourites reset on the upgrade** (simplest, lowest-risk).

### 2b. Make `ItadSourceImpl` map into the migrated models, then swap DI — ✅ DONE (merged to `dev`)
ITAD is now the live source. What shipped:
- **`/deals/v2` envelope bug fixed** (Phase-1 stub was wrong): the live API returns
  `{ nextOffset, hasMore, list:[ game{ assets, deal } ] }` — each item a game with a **singular** best
  `deal` + `assets` (boxart). `RemoteItadDealsResponse` + the singular `RemoteItadDealsGame.deal` model it.
- **ITAD→domain mappers** (`remote/itad/.../mappers/ItadDomainMappers.kt`): `ItadDeal.toDeal()`,
  `ItadGameSearchResult.toGame()`, `ItadGamePrices.toGameDetails()/toDealDetails()`, plus an `ItadMoney`
  USD formatter. **Synthesized `Deal.dealID = "<gameUUID>:<shopId>"`** (ITAD has no per-deal id);
  `gameIdFromDealId()` parses it back. CheapShark-only fields are left null.
- **The four `ItadSourceImpl` methods** implemented: `fetchDealsForStore` branches storeID
  (`/deals/v2?shops=`) / title (search → `/games/prices/v3` → cheapest deal per game) / general;
  `fetchDealDetails` parses the dealID then `/games/info/v2` + `/games/prices/v3`; `fetchGames` uses
  lookup (steamAppID) or search; `fetchGameDetails` = info + prices. Country fixed to `US`. Added
  `ItadGamesApi.getInfo` + `ItadSourceImpl.fetchGameInfo`.
- **`Deal` CheapShark-only fields → nullable** + Room **`v6→v7`** `Migration(6,7)` (drops/recreates `Deal`
  only; `7.json` committed). `DealsDao` store-deal ordering `dealRating DESC` → **`savings DESC`**.
- **`HomeViewModel.topStores`** remapped from CheapShark ids to **ITAD shop ids** (Steam 61, Epic 16, GOG 35,
  Humble 37, Fanatical 6, GreenManGaming 36, GamersGate 24, GameBillet 20, IndieGala 42).
- **DI swap (both platforms):** `cheapsharkRemoteModule` → `itadRemoteModule` in
  `app/.../GameDealsApplication.kt` + `iosApp/.../MainViewController.kt`.
- **Search filters:** title resolves via ITAD; **price/Steam-rating/Metacritic filters are no-ops** (ITAD
  has no such data) — per the ADR trade-off.

### 2c. Releases → IGDB — ✅ DONE (merged to `dev`)
ITAD has no releases endpoint, so Home "new releases" now comes from IGDB:
- `IgdbSource.fetchNewReleases(): List<Release>` — `IgdbGamesApi.buildNewReleasesQuery` posts to `/v4/games`:
  `where first_release_date != null & first_release_date <= <now> & cover != null; sort first_release_date desc;
  limit <NEW_RELEASES_LIMIT=20>;`. `<now>` (Unix seconds) comes from an injected `Clock` in `IgdbSourceImpl`.
  Mapper `RemoteIgdbGame.toReleaseOrNull()` → `Release(title=name, date=first_release_date, image=cover via
  igdbImageUrl(CoverBig))`; cover-less rows are dropped.
- `ReleasesRepository` now depends on **`IgdbSource`** (was `DealsSource`); `refreshReleases()` calls
  `igdbSource.fetchNewReleases()`. `Release` entity is **unchanged** → no Room migration. Home UI is unchanged
  (the release `date` was never displayed; clicking a release still searches deals by title via the seam).
- **`fetchReleases()` removed from the `DealsSource` seam** + both impls. CheapShark's `releaseApi` wiring was
  removed too (the `ReleaseApi`/`RemoteRelease`/`ReleaseMappers` files are now orphaned — deleted in Phase 4).
- `IgdbSourceImpl` gained a `Clock` constructor param ⇒ `igdbRemoteModule` now `IgdbSourceImpl(get(),get(),get(),get())`.

### Live ITAD API — VERIFIED in 2b (key in `local.properties: itadApiKey`)
Confirmed against the live API (curl) before/while writing the mappers:
1. **`/deals/v2` IS wrapped** — `{ nextOffset, hasMore, list }`, each item a game with a **singular `deal`**
   + `assets.boxart` (not the bare `deals[]` array the Phase-1 stub assumed). Fixed in 2b.
2. **`/service/shops/v1`** (singular) is correct and carries **no logos** → `Store` images stay blank.
3. `/deals/v2?shops=<id>` filters to that shop (`deal.shop.id == id`); `assets.boxart` is **not always
   present** (`Deal.thumb = boxart ?: ""` handles it); `deal.url` is always present.
4. `/games/prices/v3` (POST `[uuid]`) → `{ id, historyLow:{all,…}, deals:[…] }`; `/games/info/v2?id=` →
   `{ id, title, assets }`. `/games/lookup/v1?appid=` (Steam-appID → game UUID bridge) works and carries
   the appid through (`ItadSourceImpl.lookupBySteamAppId`).

---

## Phase 3a — price-history chart (DONE, branch `feat/phase3a-price-history-chart`, merged to `dev`)

The game screen now shows a real price-history line chart (#208), drawn with **Vico**. Slice 1 of 3 in
Phase 3 (user chose: staged, all three — chart → regional → bundles).

- **New seam method `DealsSource.fetchPriceHistory(gameId): PriceHistory`** + new domain model
  `domain/.../models/PriceHistory.kt` (`PriceHistory(gameID, points: List<PricePoint>)`, `PricePoint(
  timestampEpochMs, priceValue, priceDenominated)`; `@Immutable`/`@Serializable`, **not** a Room entity —
  fetched on demand, never cached). `GamesRepository.getPriceHistory(gameId)` delegates to the source.
- **ITAD**: the `/games/history/v2` façade method was renamed `fetchPriceHistory` → **`fetchItadPriceHistory`**
  (it returns the ITAD-shaped `List<ItadPriceHistoryEntry>`; the new seam method needed the `fetchPriceHistory`
  name and a 1-arg overload would have been ambiguous). New mapper `List<ItadPriceHistoryEntry>.toPriceHistory(
  gameId)` in `ItadDomainMappers.kt` — parses each entry's ISO-8601 `timestamp` via **`kotlin.time.Instant`**
  (stable in Kotlin 2.3.21, no opt-in; not `kotlinx.datetime.Instant`), drops unparseable rows, sorts oldest→
  newest. **CheapShark** can't supply a series → `CheapsharkSourceImpl.fetchPriceHistory` returns an empty
  `PriceHistory` (dead path; removed in Phase 4).
- **VM/UI**: `GameViewModel` loads price history **best-effort** (like the IGDB enrichment — a failure/empty
  series just hides the chart, never fails the screen) into `GameScreenData.Data.priceHistory`. New composable
  `feature/game/.../ui/PriceHistoryChart.kt` renders below the cheapest-ever block in both the compact and wide
  layouts; hidden when `< 2` points. y-axis = price; x-axis unlabelled with a "MonthYear – MonthYear" caption
  underneath (friendlier than per-tick dates on a compact card). New strings `game_screen_price_history_label`
  / `_range_label`.

**Vico 3.x KMP integration — non-obvious facts (vetted empirically against this repo's bleeding-edge Kotlin
2.3.21 / CMP 1.11.0):**
- Catalog: `vico = "3.2.1"`; libs `vico-compose` (`com.patrykandpatrick.vico:compose`) + `vico-compose-m3`
  (`:compose-m3`). These **3.x `compose*` artifacts ARE the multiplatform ones** (Android + iOS klibs both
  resolve & compile here). Added to `feature/game` `commonMain`. There is **no separate `:core` artifact**.
- **All classes live under `com.patrykandpatrick.vico.compose.*`** — there is **no `com.patrykandpatrick.vico.core.*`**
  package (that import fails). e.g. `compose.cartesian.{CartesianChartHost, rememberCartesianChart}`,
  `compose.cartesian.data.{CartesianChartModelProducer, lineModel}`, `compose.cartesian.layer.rememberLineCartesianLayer`,
  `compose.cartesian.axis.{VerticalAxis, HorizontalAxis}`.
- Axis builders are **companion members**, not top-level functions: call `VerticalAxis.rememberStart()` /
  `HorizontalAxis.rememberBottom()` (do **not** try to `import …axis.rememberStart`).
- Inside `modelProducer.runTransaction { … }` use **`lineModel { series(values) }`** — `lineSeries` is
  deprecated. `series(Collection<Number>)` (y-only; x auto-indexes) is the overload used.
- To discover the exact API on a version bump, unzip the AAR's `classes.jar` and `javap` the `*Kt` / `$Companion`
  classes (that's how the above was confirmed).

## Phase 3b — regional pricing (DONE, branch `feat/phase3b-regional-pricing`, merged to `dev`)

A user-selectable storefront region (#212), surfaced through a new **Settings screen**, threaded into
ITAD's `country=` param. Slice 2 of 3 in Phase 3. User decisions: **reactive immediate refresh** (changing
region reloads Home/Store now), **broad ~40-country** list, Settings entry = a **gear FAB** on Home.

- **`RegionRepository`** (new, `domain/.../repositories/region/`) — the region seam. Backed by `Storage`
  (`get(SETTINGS_QUALIFIER)`), reactive via a `MutableStateFlow<Country?>` lazily seeded from storage:
  `observeSelectedCountry(): Flow<Country>`, `getSelectedCountryCode(): String`, `setSelectedCountry()`,
  `supportedCountries`. New `Country(code, name)` model + `SUPPORTED_COUNTRIES` (~40, sorted) + `DEFAULT_COUNTRY`
  (US) in `domain/.../models/Country.kt`. Registered in `domainModule`.
- **ITAD reads the region per call**: `ItadSourceImpl` gained a `RegionRepository` ctor param (6th `get()` in
  `itadRemoteModule`); the old `COUNTRY="US"` const is gone — every `country=` now comes from
  `regionRepository.getSelectedCountryCode()`. `ItadMoney.denominated()` is now currency-symbol-aware
  (prefix `$ € £ ¥ …` for the common prefix currencies; trailing code otherwise).
- **Reactive refresh (the key mechanism):** changing region must reload cached prices. `DealsDao.clearAllDeals()`
  + `DealsRepository.clearCachedDeals()` were added. The **Settings VM clears the deal cache BEFORE persisting
  the new region** (order matters — avoids a race where Home reloads against stale cache). `HomeViewModel`
  observes `observeSelectedCountry()` and re-runs `loadTopStoresDeals()` on change (cache is empty →
  `CachedResource` refetches). `StoreViewModel` observes it and calls `retry()` (`drop(1)` skips the initial
  emission). Game/deal-detail + price-history are fetched fresh, so they reflect the new region immediately
  with no cache work. Home/Store VMs + their Koin modules gained the `RegionRepository` arg.
- **`:feature:settings`** (new module, mirrors `:feature:giveaways`): `SettingsScreen` (a `LazyColumn` region
  picker, `RadioButton` + `selectable` row a11y), `SettingsViewModel`, `settingsModule`, `settingsScreen` nav +
  `Destination.Settings`, strings. Wired into `settings.gradle.kts`, root Kover, `:app` + `:iosApp` deps and the
  Koin module lists + NavHosts on **both** platforms. Home got a Settings gear `SmallFloatingActionButton`
  (`onViewSettings`) threaded through `HomeScreen`/`HomeRoute`/`homeScreen()`.
- Tests: `RegionRepositoryTest` (in-memory fake `Storage`), `SettingsViewModelTest` (clear-then-persist order),
  and the `ItadSourceImplTest`/`HomeViewModelTest`/`StoreViewModelTest` constructors updated for the new
  `RegionRepository` dependency (`:remote:itad` has no Mokkery → hand-rolled US fake).

## Phase 4 — remove `:remote:cheapshark` (DONE, branch `feat/phase4-remove-cheapshark`, merged to `dev`) — EPIC COMPLETE

CheapShark was unused since Phase 2b (ITAD is the live `DealsSource`). Deleted the whole `:remote:cheapshark`
module + all references:
- Removed from `settings.gradle.kts`, root Kover, `:app` (`implementation` + `androidTestImplementation`),
  `:iosApp`. Dropped `cheapsharkNetworkModule`/`cheapsharkRemoteModule` from `GameDealsApplication` +
  `MainViewController`. Deleted the now-orphaned `domain/.../models/CheapsharkUrls.kt`
  (`cheapsharkDealRedirectUrl` was referenced only by the deleted CheapShark mappers). Updated the
  `DealsSource` KDoc (now names `ItadSourceImpl`).
- **Integration test:** the `:app` instrumented `HomeToStoreToDealJourneyTest` mocked CheapShark and bound it
  as the `DealsSource` in the test app — stale since 2b/2c. **Dropped** it + its CheapShark JSON fixtures
  (user decision); stripped CheapShark from `TestNetworkOverridesModule` + `FixtureRequestHandler` (kept the
  GamerPower/giveaways override) and switched `TestGameDealsApplication` to bind ITAD (mirrors production).
  *Follow-up (not done): re-add an ITAD-based journey test.*
- **Latent test gap fixed:** the full `testAndroidHostTest` here ran `:feature:game` (previously the
  pre-existing `:remote:igdb` failure short-circuited the run without `--continue`, masking it). `GameViewModelTest`
  needed a `getPriceHistory` stub (added in 3a but never stubbed) so the Data-state assertions match the default
  empty `priceHistory`. Use `--continue` when running the full suite so module failures don't mask each other.

> Remaining for the repo (outside epic #205): the pre-existing unrelated `IgdbSourceImplTest.fetchGameDetailsByTitle_…`
> failure still fails on `dev`.

## Phase 3c — bundles (DONE, branch `feat/phase3c-bundles`, merged to `dev`) — Phase 3 COMPLETE

Storefront bundles from ITAD `/bundles/v1` (region-aware). Slice 3 of 3. User decisions: a **Home
"Bundles" strip + a dedicated Bundles list screen**, and an **in-app bundle detail screen** (games + price,
with a "Get bundle" button to the store URL).

- **Live shape verified** (curl): `/bundles/v1?country=` returns a **bare array** of bundles —
  `{ id, title, page{name,shopId}, url (affiliate), details (ITAD page), publish, expiry, counts{games,media},
  tiers:[{ price, addon, games:[{id,title,assets.boxart}] }] }`. Tier games share the search-game shape.
- **Implementation calls (made, not asked):** bundles go through the **`DealsSource` seam** as `fetchBundles()`
  (CheapShark returns `emptyList()`, like `fetchPriceHistory`); and they are **fetched fresh, NOT Room-cached**
  — the nested `tiers→games` structure would make an entity/migration painful, and fresh fetch keeps them
  region-accurate. The DTO maps **straight to the domain `Bundle`** (no `ItadBundle` intermediate, since bundles
  are ITAD-only): `RemoteItadBundle.toBundle()` (in `ItadDomainMappers.kt`) — headline price = **cheapest tier**,
  games = **union across tiers** (deduped by id), expiry parsed via `kotlin.time.Instant`.
- **Layers:** new `ItadBundlesApi` (registered in `itadNetworkModule`; 7th `get()` into `ItadSourceImpl`),
  `RemoteItadBundle*` DTOs, domain `Bundle(+BundleGame)` model (non-entity), `fetchBundles()` on the seam,
  `BundlesRepository` (`getBundles()` / `getBundle(id)` = fetch-and-find). `ItadSourceImpl.fetchBundles` reads
  the region from `RegionRepository` (#212).
- **`:feature:bundles`** (new module): `BundlesScreen` (list, loading/error/empty states + retry),
  `BundleDetailScreen` (store/expiry/price + included-games list + "Get bundle" → `goToWeb(url)`), two VMs (one-shot
  load with Loading/Error/Data), DI, nav (`Destination.Bundles` + `Destination.BundleDetail(bundleId)`), strings.
  Wired into `settings.gradle.kts`, root Kover, `:app` + `:iosApp` deps + Koin module lists + NavHosts on both
  platforms.
- **Home:** a "Bundles" strip (`HomeBundleRow` + "View All Bundles") loaded **best-effort** into
  `HomeScreenData.bundles` (a 4th `flatMapLatest` in `loadTopStoreDataFlow`); strip rows → bundle detail, "see all"
  → Bundles screen. Threaded `onViewBundles`/`onViewBundle` through `HomeScreen`/`HomeRoute`/`homeScreen()`.
- Tests: `BundlesRepositoryTest`, `ItadSourceImplTest` (`/bundles/v1` route + cheapest-price/union-games mapping),
  `BundlesViewModelTest` + `BundleDetailViewModelTest`; updated `HomeViewModelTest`/`HomeScreenTest` for the new
  `bundlesRepository` dep + Home callbacks.

## Phase 0 — what shipped (reference)

Seam decouple, **no behavior change** (still CheapShark). Commits `87f204f` (code), `c08f366` (ADR).

- Renamed the domain port `CheapsharkSource` → **`DealsSource`** (`domain/.../source/DealsSource.kt`). Impl
  class stays `CheapsharkSourceImpl`; injected field renamed `cheapsharkSource` → `dealsSource`.
- Pushed the deal/store URL into the models so UI/share stop building CheapShark redirect links:
  - `Deal.url` and `Store.iconUrl` → **computed properties** (derived from persisted `dealID` / `images`) →
    **no Room column, no schema migration** (this was a deliberate decision — see Decisions).
  - `DealDetails.CheaperStore.url` and `GameDetails.GameDeal.url` → **mapper-filled** fields
    (`remote/cheapshark/.../mappers/{DealMappers,GameMappers}.kt`).
  - Repointed `common/ui/.../deal/DealBottomSheet.kt`, `common/ui/.../share/ShareText.kt` (builder param
    `dealId` → `dealUrl`), and `feature/game/.../GameScreen.kt` to read `.url`. Threaded `dealUrl` through
    `DealBottomSheetData` → `DealDetailsController` → Store/Home viewmodels → screens.
- `cheapsharkDealRedirectUrl` (in `domain/.../models/CheapsharkUrls.kt`) is now referenced only by the
  CheapShark mappers + the computed `Deal.url` — never by UI/share. (Decision: keep it in `:domain`.)

## Phase 1 — what shipped (reference)

New module `:remote:itad`, mirroring `:remote:cheapshark`/`:remote:igdb`. Commits `91f9911` (module +
Android), `5b830b4` (iOS). Not wired as the live source.

Module tree (`remote/itad/src/commonMain/kotlin/pm/bam/gamedeals/remote/itad/`):
- `logic/ItadHttpClient.kt` — Ktor client → `https://api.isthereanydeal.com`, sends `ITAD-API-Key` header.
- `auth/ItadCredentials.kt` — `data class ItadCredentials(val apiKey: String)`.
- `models/RemoteItadModels.kt` — ITAD v2 transport DTOs (money = `{amount, amountInt, currency}`).
- `models/ItadModels.kt` — clean ITAD-shaped output models (`ItadMoney`, `ItadShop`, `ItadDeal`,
  `ItadGamePrices`, `ItadPriceHistoryEntry`, `ItadGameSearchResult`).
- `mappers/ItadMappers.kt` — DTO → ITAD models, plus `RemoteItadShop.toStore()` (the one clean domain bridge).
- `api/{ItadShopsApi, ItadDealsApi, ItadGamesApi}.kt` — shops / deals / search+lookup+prices+history.
- `ItadSourceImpl.kt` — implements `DealsSource`: `fetchStores()` works; the rest **throw
  `UnsupportedOperationException` (TODO → Phase 2)**. Real fetch+map lives on the ITAD-shaped façade methods.
- `di/RemoteNetworkModule.kt` (`itadNetworkModule`, `ITAD_QUALIFIER`) and `di/RemoteModule.kt`
  (`itadRemoteModule` — the `DealsSource` binding, **not registered yet**).
- Tests: `commonTest/.../ItadSourceImplTest.kt` (MockEngine; covers fetch+map of stores/deals/prices/history,
  the `ITAD-API-Key` header, and that the unimplemented methods throw).

Key wiring added:
- Android: `app/build.gradle.kts` reads `itadApiKey` (local.properties / env `ITAD_API_KEY`) →
  `buildConfigField ITAD_API_KEY`; `GameDealsApplication` registers `ItadCredentials` + `itadNetworkModule`.
- iOS: `iosApp/iosApp/Info.plist` maps `ITADApiKey` → `$(ITAD_API_KEY)`; `MainViewController` registers
  `ItadCredentials(infoPlistString("ITADApiKey"))` + `itadNetworkModule`. Key value lives in the **gitignored**
  `iosApp/Secrets.xcconfig` (`ITAD_API_KEY`).
- `:remote:itad` added to `settings.gradle.kts`, root `build.gradle.kts` Kover list, and `:app` + `:iosApp` deps.

---

## Build / verification knowledge (don't rediscover this)

- **JAVA_HOME**: `export JAVA_HOME=/opt/android-studio/jbr` before any `./gradlew` (JDK 21).
- **`./gradlew build` fails locally** only at `:app:validateSigningRelease` — missing `upload_keystore.jks`
  (an env/secret), **not a code problem**. Locally use `./gradlew assembleDebug test` instead. CI has the key.
- **iOS on this Linux host**: Kotlin/Native **compiles iOS klibs on Linux** (only final framework *linking*
  needs macOS). Verify iOS Kotlin with `./gradlew :iosApp:compileKotlinIosSimulatorArm64`. iOS targets
  (`iosArm64`, `iosSimulatorArm64`) are registered unconditionally by the KMP convention plugin.
- **Compose stability**: per-module baselines in `<module>/stability/*.stability`; `./gradlew debugStabilityCheck`
  gates them; regenerate with `./gradlew debugStabilityDump` if you change `@Immutable`/composable signatures.
- **Coverage**: add new modules to the `kover(project(":…"))` list in root `build.gradle.kts`.
- **No Koin module-verification test** exists (nothing to update when adding modules).
- **Test harness**: `pm.bam.gamedeals.testing.mockHttpClient(json) { request -> respond(...) }` (MockEngine +
  `expectSuccess` + JSON). Shared `Json` has `ignoreUnknownKeys = true` (lean DTOs are fine).
  `RemoteExceptionTransformer { it }` (SAM) for the identity transform. MockK doesn't run on K/N — use
  hand-rolled fakes in `commonTest`. Source error pipeline: `.log(logger, TAG).mapAnyFailure {
  remoteExceptionTransformer.transformApiException(this) }.getOrThrow()`.
- Namespace for a new `:remote:x` module auto-derives to `pm.bam.gamedeals.remote.x` (convention plugin).

---

## Room migration — the test-enforced rules (critical for Phase 2)

- DB version is **`DOMAIN_DB_VERSION = 5`** (`domain/.../db/DomainDatabase.kt`), `exportSchema = true`,
  schemas committed under `domain/schemas/pm.bam.gamedeals.domain.db.DomainDatabase/<version>.json`.
- Entities: `Deal, Game, Store, Release, Giveaway, FavouriteGame`. (`DealDetails`, `GameDetails` are **not**
  entities — no migration concern for their fields.)
- `domain/src/androidHostTest/.../db/DomainDatabaseMigrationTest.kt` **fails the build** unless:
  1. a `<version>.json` is committed for the current version, AND
  2. **every** transition from v5 upward is registered in `DOMAIN_MIGRATIONS` or `DOMAIN_AUTO_MIGRATIONS`
     (`domain/.../db/Migrations.kt`). Note: `fallbackToDestructiveMigrationFrom(...)` in `DomainModule.kt`
     (currently covers 1–4) does **NOT** satisfy this test on its own.
- So bumping the version ⇒ register a migration (manual or `@AutoMigration(from, to)` on `@Database` +
  add `from to to` to `DOMAIN_AUTO_MIGRATIONS`) ⇒ regenerate the schema via
  `./gradlew :domain:kspAndroidMain` and commit the new `<version>.json`. Additive column changes are
  AutoMigration-friendly (give non-null columns a `@ColumnInfo(defaultValue = …)`).

---

## Decisions already made (don't relitigate)

- **Phase 0 skipped the Room migration on purpose** (user call): entity URL fields are computed, not stored,
  so v5 schema is unchanged. The real migration is deferred to Phase 2.
- **Phase 1 = "ITAD-shaped models, honest gaps"**: map ITAD into clean module-local models; `ItadSourceImpl`
  implements only what fits today and throws elsewhere (rather than forcing UUIDs into Int-id models with a
  throwaway hash). `fetchReleases()` throws (no ITAD endpoint).
- `cheapsharkDealRedirectUrl` stays in `:domain`.
- ITAD API key sent via the `ITAD-API-Key` **header** (ITAD also accepts a `key` query param).

---

## Quick file map

| Concern | Path |
|--------|------|
| Seam (port) | `domain/.../source/DealsSource.kt` |
| Domain models | `domain/.../models/{Deal,Store,Game}.kt`, `CheapsharkUrls.kt` |
| CheapShark source | `remote/cheapshark/...` (template + still-live impl) |
| ITAD source | `remote/itad/...` |
| Live-source DI (swap here in P2) | `app/.../GameDealsApplication.kt`, `iosApp/.../MainViewController.kt` |
| Room | `domain/.../db/{DomainDatabase,Migrations}.kt`, `domain/.../di/DomainModule.kt`, `domain/schemas/`, `domain/src/androidHostTest/.../db/DomainDatabaseMigrationTest.kt` |
| Key wiring | `app/build.gradle.kts`, `iosApp/iosApp/Info.plist`, `iosApp/Secrets.xcconfig` (gitignored) |
| IGDB (for releases move) | `remote/igdb/...`, `domain/.../source/IgdbSource.kt` |
