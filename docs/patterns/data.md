---
**Path scope:** `domain/**`, `remote/**`
**Last surveyed:** 34b01013 on 2026-05-18
---

# Data

The data layer is built around a **domain-first facade**: repositories sit in `:domain`, expose only domain models via interface-based data sources, and delegate transport concerns to sub-modules under `:remote:*`. Persistence (Room KMP) and retrieval (Flow) are co-located inside repositories. A lightweight, `Clock`-injected `CachedResource` handles TTL refresh. Transport is now Ktor with kotlinx.serialization, wrapped by Sandwich-Ktor for `ApiResponse`-based error handling.

## Patterns

### Repository-as-Facade with Domain-Only Ports

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** DealsRepository, StoresRepository, GamesRepository, GiveawaysRepository, ReleasesRepository

**The pattern.**
Each repository is a Koin-bound `single { … }` class that:
1. Exposes public APIs returning only domain models (e.g., `Deal`, `Store`, `Giveaway`).
2. Takes a *single* source interface (e.g., `CheapsharkSource`, `GamerPowerSource`) that lives in `:domain` but is implemented in `:remote:*`.
3. Manages local DAO access directly (DAOs are constructor parameters).
4. Orchestrates read-before-refresh and transactional writes internally.

**Why this works for us.**
Repositories are single-responsibility — they don't know about DTOs, HTTP clients, or mappers. The domain-side source interface is a declarative contract that can be swapped without touching repository code. Constructor injection works identically under Koin, so the boundary shape is unchanged from the Hilt era; only the binding DSL moved.

**Known trade-offs / when it strains.**
No explicit "local vs remote data source" split — repositories conflate cache and write logic. Works here because refresh is synchronous and simple; would not scale to streaming sync or conflict resolution.

**How to apply it.**
```kotlin
class MyRepository(
  private val myDao: MyDao,
  private val mySource: MySource              // interface defined in :domain
) {
  fun observe(): Flow<List<MyModel>> = myDao.observeAll()

  suspend fun refresh() {
    val fresh = mySource.fetch()
    myDao.insert(*fresh.toTypedArray())
  }
}

// Koin binding
val domainModule = module {
  single { MyRepository(get(), get()) }
}

// :domain
interface MySource { suspend fun fetch(): List<MyModel> }

// :remote
class MySourceImpl(...) : MySource {
  override suspend fun fetch() = api.get().map { it.toModel(...) }
}
```

**Seen in.**
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/stores/StoresRepository.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/games/GamesRepository.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/giveaway/GiveawaysRepository.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/releases/ReleasesRepository.kt

### TTL-Driven Cache Seam (`CachedResource`)

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** DealsRepository, StoresRepository

**The pattern.**
A reusable `CachedResource<T>` class encapsulates "read cache, check expiry, refresh if needed". It takes `clock`, `read: suspend () -> List<T>`, `expiresAtMillis: (T) -> Long`, and `refresh: suspend () -> Unit`. `refreshIfNeeded(force)` reads current items, checks any are stale via the injected `Clock`, and runs `refresh()` only if needed. Repositories wire one instance per logical resource (e.g., `storeDealsCache(storeId)`). Entities carry an `expires: Long` field stamped at insert time.

**Why this works for us.**
Cache logic is testable as pure Kotlin — no Room or time mocking needed. Centralizes the "any-entry-stale = whole-cache-stale" decision (simple, but reasonable for bounded entity sets). TTL is a property of the resource, not the retrieval path.

**Known trade-offs / when it strains.**
No per-entity expiry — if any Store is stale, all stores refresh. No streaming invalidation; refresh is explicit. The pattern requires `Clock` to be injected globally.

**How to apply it.**
```kotlin
private fun storeDealsCache(storeId: Int) = CachedResource(
  clock = clock,
  read = { dealsDao.getStoreDeals(storeId) },
  expiresAtMillis = { it.expires },
  refresh = {
    val expiresAt = clock.nowMillis() + DEALS_TTL_MILLIS
    // Transactional write via Room KMP's writer-connection API — see resources.md
    // (useWriterConnection { immediateTransaction { … } })
    domainDatabase.runDealsRefreshTxn(storeId, expiresAt)
  }
)
```

**Seen in.**
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/cache/CachedResource.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/stores/StoresRepository.kt

### Sandwich `ApiResponse` + `RemoteExceptionTransformer`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** CheapsharkSourceImpl, GamerPowerSourceImpl

**The pattern.**
Remote sources use **Sandwich-Ktor** to wrap Ktor responses in `ApiResponse<T>`, enabling non-throwing handling of HTTP errors. Unlike the Retrofit era — where Sandwich call adapters auto-wrapped returns — Ktor doesn't produce `ApiResponse` natively. So each API class explicitly wraps the call: a `try` block calls `httpClient.get(...).body<T>()` and returns `ApiResponse.Success(...)`; the `catch (t: Throwable)` returns `ApiResponse.exception(t)`. The source impl then chains `.log()` (debug logging), `.mapAnyFailure()` (exception conversion via `RemoteExceptionTransformer`), and `.getOrThrow()`. Mappers run *after* unwrapping, so they never see failed responses.

**Why this works for us.**
`expectSuccess = true` on the Ktor client turns 4xx/5xx into thrown `ResponseException`, which the try/catch pipes into `ApiResponse.exception`. The unwrapping chain is identical to before, so domain callers never see Ktor types — `RemoteExceptionTransformer` remains their only exception bridge.

**Known trade-offs / when it strains.**
The try/catch wrapping is per-endpoint boilerplate (the Retrofit adapter used to do this implicitly). A small `runApi { … }` helper could absorb it, but explicitness has been preferred so far. Error response bodies are discarded by `mapAnyFailure`; preserving them would require keeping `ApiResponse` longer.

**How to apply it.**
```kotlin
// :remote — explicit wrapping (Ktor doesn't auto-wrap)
suspend fun getDeals(params: SearchParameters): ApiResponse<List<RemoteDeal>> =
  try {
    ApiResponse.Success(httpClient.get("deals") { /* params */ }.body())
  } catch (t: Throwable) {
    ApiResponse.exception(t)
  }

// source impl — unchanged unwrap chain
override suspend fun fetchDealsForStore(query: SearchParameters?): List<Deal> =
  dealsApi.getDeals(...)
    .log(logger, tag = TAG)
    .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
    .getOrThrow()
    .map { it.toDeal(currencyTransformation) }
```

**Seen in.**
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/exceptions/RemoteExceptionTransformer.kt
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/Extensions.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt
- remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/GamerPowerSourceImpl.kt

**Related lessons.** L-2026-05-03-03

### Extension-Function DTO ↔ Domain Mappers

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all remote API response types

**The pattern.**
Transport DTOs (e.g., `RemoteDeal`, `RemoteDealDetails`) gain `internal` extension functions (`RemoteDeal.toDeal`, `RemoteDealDetails.toDealDetails`) that accept context (e.g., `CurrencyTransformation`, `DateTimeFormatter`) and return domain models. Mappers live in the same `:remote:*` sub-module as the DTOs. Composed mappers handle nested structures.

**Why this works for us.**
DTOs are not exposed outside the remote module. Extension functions are lightweight, testable, and idiomatic Kotlin. Context (currency, timezone) is injected once per source impl and passed through the mapping chain, avoiding global state.

**Known trade-offs / when it strains.**
Extension functions can't override `equals`/`hashCode` or implement interfaces — if the domain model needs to depend on DTO behavior, concrete mapper classes would be needed. Deeply nested structures spawn deeply nested extensions; at scale, a builder pattern might be clearer.

**How to apply it.**
```kotlin
internal fun RemoteDeal.toDeal(currencyTransformation: CurrencyTransformation): Deal =
  Deal(
    dealID = dealID,
    title = title,
    salePriceDenominated = currencyTransformation.valueToDenominated(salePrice),
    // ...
  )
```

**Seen in.**
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/mappers/DealMappers.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/mappers/GameMappers.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/mappers/StoreMappers.kt
- remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/mappers/GiveawayMappers.kt

### Room Entities + DAOs with Flow Observables

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** DealsDao, StoresDao, GamesDao, GiveawaysDao, ReleasesDao

**The pattern.**
Each entity is a Room `@Entity` with a `@PrimaryKey` field (typically the API resource ID), `@Serializable` + `@SerialName` annotations for kotlinx.serialization, and an `expires: Long` field for TTL. DAOs expose `Flow<List<T>>` for observation, `suspend` methods for one-shot reads, and `suspend` insert/delete. Room KMP is wired via the `androidx-sqlite-bundled` driver, so the `@Entity` / `@Dao` annotations themselves are unchanged from the JVM-only Room era — only the database builder and driver setup move into platform code.

**Why this works for us.**
Flow observables let repositories emit fresh data without polling or manual refresh signaling. `@Serializable` annotations enable potential local export without adding migration logic. Suspend reads run safely off the main thread. Room KMP lets us keep the schema definition in `commonMain` alongside the rest of the data layer.

**Known trade-offs / when it strains.**
`fallbackToDestructiveMigration()` is used (acceptable for a consumer app, not for production data integrity). No delete cascade — orphaned rows are possible but mitigated by overwriting on refresh. Query compile-time validation is limited to simple SQL.

**How to apply it.**
```kotlin
@Dao
interface DealsDao {
  @Query("SELECT * FROM Deal WHERE storeID IS :storeId ORDER BY dealRating DESC")
  fun observeStoreDeals(storeId: Int): Flow<List<Deal>>

  @Query("SELECT * FROM Deal WHERE storeID IS :storeId ORDER BY dealRating DESC")
  suspend fun getStoreDeals(storeId: Int): List<Deal>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addDeals(vararg deals: Deal)
}
```

**Seen in.**
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/db/dao/DealsDao.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/db/DomainDatabase.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/models/Deal.kt

### Ktor `HttpClient` Factory (per-vendor singletons via Koin)

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** Cheapshark + GamerPower transports

**The pattern.**
Each vendor gets its own `HttpClient` singleton built by a shared `gameDealsHttpClient(...)` factory that lives in `commonMain`. The factory installs `ContentNegotiation` with kotlinx.serialization JSON, configures `HttpTimeout` (connect 10s, request 30s), and sets `expectSuccess = true` so 4xx/5xx throw `ResponseException` (caught by the source-impl try/catch wrapper described in the Sandwich entry). Bindings use per-vendor Koin qualifiers — exported as `CHEAPSHARK_QUALIFIER` / `GAMERPOWER_QUALIFIER` (`named("cheapshark")`, `named("gamerpower")`) — to prevent cross-vendor binding collisions. The platform engine (OkHttp on Android, Darwin on iOS) is wired via an `expect`/`actual` factory; see `kmp.md` for that boundary's mechanics.

**Why this works for us.**
Per-vendor clients keep vendor-specific defaults (base URL, headers, interceptors) isolated — a Cheapshark tweak can't accidentally affect GamerPower. `expectSuccess = true` keeps the error model uniform across the codebase. ContentNegotiation + kotlinx.serialization replaces Moshi/Gson and works in `commonMain` without per-target boilerplate.

**Known trade-offs / when it strains.**
Two `HttpClient` instances mean two engine resource pools; for two vendors this is negligible. Per-vendor qualifiers add ceremony at injection sites. Engine-level customization (e.g. an OkHttp-only interceptor) has to flow through `expect`/`actual`, which adds a layer.

**How to apply it.**
```kotlin
// commonMain — vendor-agnostic factory
fun gameDealsHttpClient(
  engine: HttpClientEngine,
  baseUrl: String,
  json: Json,
): HttpClient = HttpClient(engine) {
  expectSuccess = true
  install(ContentNegotiation) { json(json) }
  install(HttpTimeout) {
    connectTimeoutMillis = 10_000
    requestTimeoutMillis = 30_000
  }
  defaultRequest { url(baseUrl) }
}

// Koin module — per-vendor qualifiers
val remoteModule = module {
  single(CHEAPSHARK_QUALIFIER) {
    gameDealsHttpClient(get(), CHEAPSHARK_BASE_URL, get())
  }
  single(GAMERPOWER_QUALIFIER) {
    gameDealsHttpClient(get(), GAMERPOWER_BASE_URL, get())
  }
}
```

**Seen in.**
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/GameDealsHttpClient.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/di/RemoteNetworkModule.kt
- remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/di/RemoteNetworkModule.kt

**Related lessons.** L-2026-04-30-06, L-2026-05-03-02, L-2026-05-03-03, L-2026-05-04-04

**Related.** See `kmp.md` for the `expect`/`actual` engine factory; `kmp.md` for kotlinx.serialization `Json` configuration.

### Coil 3 ImageLoader with `KtorNetworkFetcherFactory`

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all Compose image loads in `:app`

**The pattern.**
A single `ImageLoader` is bound as a Koin `single` in `:app`'s `AppModule.kt` (Android-only at the moment — iOS doesn't ship image loading yet). The builder registers Coil 3's `KtorNetworkFetcherFactory()` as a component so image fetches ride the same Ktor transport stack as the API clients. Compose call sites pick the loader up via `AsyncImage(..., imageLoader = koinInject())` (or the app sets it as the singleton via `setSingletonImageLoaderFactory`).

**Why this works for us.**
One transport for everything — JSON and images share the same engine, timeout policy, and (in principle) interceptors. Cancellation propagates through Compose's coroutine scope, so navigating away cancels in-flight image fetches. No separate OkHttp setup is needed just for images.

**Known trade-offs / when it strains.**
Coil 3 + Ktor is a relatively new pairing; the Ktor fetcher doesn't expose every OkHttp interceptor knob. Image-cache tuning lives in the builder, not in the Ktor client config, so the two stacks can drift in subtle ways (e.g., cache-control handling).

**How to apply it.**
```kotlin
// :app AppModule.kt
val appModule = module {
  single<ImageLoader> {
    ImageLoader.Builder(androidContext())
      .components { add(KtorNetworkFetcherFactory()) }
      .build()
  }
}
```

**Seen in.**
- app/src/main/java/pm/bam/gamedeals/di/AppModule.kt

## What we don't do

- **No multi-source merging in repositories.** Each repository has one source facade. **Why we avoid it:** merging two remote sources requires explicit conflict and ordering rules — adding it on demand keeps repositories simple.
- **No per-field TTL.** Cache expiry is per-entity, not per-field. **Why we avoid it:** field-level TTL is overkill for the bounded entity sets here.
- **No offline-first sync.** No local mutation queue, conflict resolution, or sync graph. Repositories assume connectivity for refresh; network failure halts the operation. **Why we avoid it:** the app is read-mostly; introducing offline writes would be a major architectural shift.
- **No streaming refresh.** No WebSockets or SSE — all fetches are explicit request-response. **Why we avoid it:** the source APIs don't push; polling is simpler.
- **No request deduplication.** Multiple simultaneous calls to a `refresh*()` will each fetch. **Why we avoid it:** the surface area where this matters has been small enough that the simpler approach wins.
- **No separate `LocalDataSource` / `RemoteDataSource` layers.** Repositories directly take DAOs and source interfaces as constructor params. **Why we avoid it:** the second indirection adds noise without buying anything for one-way refresh patterns.

## Decommissioned

### Paging 3 `RemoteMediator` for Infinite Scroll

**Status:** deprecated (Paging 3 + RemoteMediator removed during KMP migration 2026-05; deals are fetched non-paged. No replacement.)
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** DealsRepository pagination of store deals

**The pattern.**
`DealsMediator(RemoteMediator<Int, Deal>)` handles pagination: takes `storeId` + `pageSize`, injects `CheapsharkSource` and `DomainDatabase`. Tracks the current page in a separate `DealPage` entity. On `REFRESH`: clears all deals for the store, fetches page 0; on `APPEND`: increments page and fetches; on `PREPEND`: returns success immediately. Returns `MediatorResult.Success(endOfPaginationReached = deals.isEmpty())`. The fetch-clear-insert is wrapped in `DomainDatabase.withTransaction` for atomicity.

**Why this works for us.**
Paging 3 handles scroll position, placeholders, and UI state; the mediator just orchestrates remote + local. Transactional inserts prevent partial pages from being visible. Page state is persistent (in Room), so position survives configuration changes.

**Known trade-offs / when it strains.**
Mediator is stateful and created per screen — not reusable across multiple paged lists. No retry policy — failed fetches return `MediatorResult.Error` and the UI must handle it. Large pages (60 items here) inserted on every append can cause UI jank; batching would help.

**How to apply it.**
```kotlin
@OptIn(ExperimentalPagingApi::class)
fun getPagingStoreDeals(storeId: Int): Flow<PagingData<Deal>> = Pager(
  config = PagingConfig(pageSize = 60, enablePlaceholders = false),
  remoteMediator = DealsMediator(domainDatabase, cheapsharkSource, storeId, 60, logger)
) {
  dealsDao.getPagingStoreDeals(storeId)
}.flow
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/paging/DealsMediator.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt
