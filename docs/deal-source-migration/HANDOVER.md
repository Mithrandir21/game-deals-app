he # Deal-source migration (CheapShark → ITAD) — Handover

**Epic:** [#205](https://github.com/Mithrandir21/game-deals-android-app/issues/205) · **ADR:** [`docs/adr/0001-deal-source-itad.md`](../adr/0001-deal-source-itad.md)
**Status:** Phases 0 ✅, 1 ✅, **2a ✅** (domain UUID models + Room `v5→v6`), and **2c ✅** (releases → IGDB)
are merged to `dev`. **Next up: Phase 2b (ITAD mapping + DI swap).**

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
| 2b | Map `ItadSourceImpl` into the migrated models + DI swap; CheapShark-only `Deal` fields → nullable (`v6→v7`) + UI null-tolerance | TODO | (no issue yet) |
| 3 | Real price-history chart + bundles + regional pricing | TODO | #208, #212 |
| 4 | Remove `:remote:cheapshark` | TODO | (no issue yet) |

CheapShark is still the **live** source. ITAD is built and tested but **not wired** as the active
`DealsSource` yet — that swap is the heart of Phase 2.

Related memory note (auto-loaded for Claude sessions): `.claude/.../memory/itad-deal-source-migration.md`.

---

## Phase 2 — start here

Goal: make ITAD the live source. Three workstreams; **2a and 2c are done**, do **2b next**.

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

### 2b. Make `ItadSourceImpl` map into the migrated models, then swap DI
- Fill in the methods `ItadSourceImpl` currently throws on (`fetchDealsForStore`, `fetchDealDetails`,
  `fetchGames`, `fetchGameDetails`) by mapping ITAD → the migrated models. The ITAD-shaped façade methods
  already exist and do the fetch+map (`fetchDeals`, `fetchGamePrices`, `fetchPriceHistory`, `searchGames`,
  `lookupBySteamAppId`) — bridge those into the domain mappers.
- **DI swap (do this on BOTH platforms):** register `itadRemoteModule` and remove
  `cheapsharkRemoteModule`'s `DealsSource` binding. They both `single<DealsSource> { … }` — registering both
  at once clashes. `itadNetworkModule` + `ItadCredentials` are **already** registered on both platforms.
  - Android: `app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt`
  - iOS: `iosApp/src/iosMain/kotlin/pm/bam/gamedeals/iosApp/MainViewController.kt`

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

### Confirm against a live ITAD key (two code TODOs)
You'll need a real key (`local.properties: itadApiKey=…` for Android; `Secrets.xcconfig: ITAD_API_KEY` for
iOS) to confirm:
1. **`/deals/v2` envelope** — modelled as a bare JSON array; ITAD may wrap it in
   `{ list, nextOffset, hasMore }`. If so add a `RemoteItadDealsResponse` wrapper in `ItadDealsApi`.
2. **Shops endpoint** — used `/service/shops/v1` (singular; #207 cited `/services/`), and ITAD's shops list
   carries no logos, so `Store.images` is blank from ITAD. Confirm path + whether logos come from elsewhere.

Also keep the **Steam-appID → IGDB bridge** working: `ItadSourceImpl.lookupBySteamAppId(appid)` resolves a
Steam appid to an ITAD game (and carries the appid through); keep the title-based fallback.

---

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
