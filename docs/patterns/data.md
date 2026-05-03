---
**Path scope:** `domain/**`, `remote/**`
**Last surveyed:** 31a89bc on 2026-05-03
---

# Data

The data layer is built around a **domain-first facade**: repositories sit in `:domain`, expose only domain models via interface-based data sources, and delegate transport concerns to sub-modules under `:remote:*`. Persistence (Room) and retrieval (Flow) are co-located inside repositories. A lightweight, `Clock`-injected `CachedResource` handles TTL refresh.

## Patterns

### Repository-as-Facade with Domain-Only Ports

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** DealsRepository, StoresRepository, GamesRepository, GiveawaysRepository, ReleasesRepository

**The pattern.**
Each repository is a `@Singleton` Hilt-provided class that:
1. Exposes public APIs returning only domain models (e.g., `Deal`, `Store`, `Giveaway`).
2. Injects a *single* source interface (e.g., `CheapsharkSource`, `GamerPowerSource`) that lives in `:domain` but is implemented in `:remote:*`.
3. Manages local DAO access directly (DAOs are constructor-injected).
4. Orchestrates read-before-refresh and transactional writes internally.

**Why this works for us.**
Repositories are single-responsibility — they don't know about DTOs, HTTP clients, or mappers. The domain-side source interface is a declarative contract that can be swapped without touching repository code. Callers (features, ViewModels) depend only on domain models.

**Known trade-offs / when it strains.**
No explicit "local vs remote data source" split — repositories conflate cache and write logic. Works here because refresh is synchronous and simple; would not scale to streaming sync or conflict resolution.

**How to apply it.**
```kotlin
@Singleton
class MyRepository @Inject constructor(
  private val myDao: MyDao,
  private val mySource: MySource              // interface defined in :domain
) {
  fun observe(): Flow<List<MyModel>> = myDao.observeAll()

  suspend fun refresh() {
    val fresh = mySource.fetch()
    myDao.insert(*fresh.toTypedArray())
  }
}

// :domain
interface MySource { suspend fun fetch(): List<MyModel> }

// :remote
class MySourceImpl @Inject constructor(...) : MySource {
  override suspend fun fetch() = api.get().map { it.toModel(...) }
}
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/stores/StoresRepository.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/games/GamesRepository.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/giveaway/GiveawaysRepository.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/releases/ReleasesRepository.kt

### TTL-Driven Cache Seam (`CachedResource`)

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
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
    domainDatabase.withTransaction {
      dealsDao.clearDealsForStore(storeId)
      cheapsharkSource.fetchDealsForStore(SearchParameters(storeID = storeId, pageSize = 60))
        .map { it.copy(expires = expiresAt) }
        .let { dealsDao.addDeals(*it.toTypedArray()) }
    }
  }
)
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/cache/CachedResource.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/stores/StoresRepository.kt

### Sandwich `ApiResponse` + `RemoteExceptionTransformer`

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** CheapsharkSourceImpl, GamerPowerSourceImpl

**The pattern.**
Remote sources use the **Sandwich** library to wrap Retrofit results in `ApiResponse<T>`, enabling non-throwing handling of HTTP errors. Each `*Api` interface returns `ApiResponse<T>`. The source impl chains `.log()` (debug logging), `.mapAnyFailure()` (exception conversion via `RemoteExceptionTransformer`), then `.getOrThrow()`. Mappers are called *after* unwrapping, so they never see failed responses.

**Why this works for us.**
Retrofit natively throws on HTTP errors; Sandwich turns them into result types, avoiding try/catch chains. `log()` and `mapAnyFailure()` are reusable extensions, reducing per-endpoint boilerplate. Domain callers never see Retrofit — `RemoteExceptionTransformer` is their only exception bridge.

**Known trade-offs / when it strains.**
Adds a third-party dependency (skydoves Sandwich). If we needed to drop it, unwrapping would become verbose. Error response bodies are discarded by `mapAnyFailure`; preserving them would require keeping `ApiResponse` longer.

**How to apply it.**
```kotlin
override suspend fun fetchDealsForStore(query: SearchParameters?): List<Deal> =
  dealsApi.getDeals(...)
    .log(logger, tag = TAG)
    .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
    .getOrThrow()
    .map { it.toDeal(currencyTransformation) }
```

**Seen in.**
- remote/src/main/java/pm/bam/gamedeals/remote/exceptions/RemoteExceptionTransformer.kt
- remote/src/main/java/pm/bam/gamedeals/remote/logic/Extensions.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt
- remote/gamerpower/src/main/java/pm/bam/gamedeals/remote/gamerpower/GamerPowerSourceImpl.kt

### Extension-Function DTO ↔ Domain Mappers

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
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
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/mappers/DealMappers.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/mappers/GameMappers.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/mappers/StoreMappers.kt
- remote/gamerpower/src/main/java/pm/bam/gamedeals/remote/gamerpower/mappers/GiveawayMappers.kt

### Room Entities + DAOs with Flow Observables

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** DealsDao, StoresDao, GamesDao, GiveawaysDao, ReleasesDao

**The pattern.**
Each entity is a Room `@Entity` with a `@PrimaryKey` field (typically the API resource ID), `@Serializable` + `@SerialName` annotations for kotlinx.serialization, and an `expires: Long` field for TTL. DAOs expose `Flow<List<T>>` for observation, `suspend` methods for one-shot reads, `suspend` insert/delete, and `PagingSource<K, T>` for Paging 3.

**Why this works for us.**
Flow observables let repositories emit fresh data without polling or manual refresh signaling. `@Serializable` annotations enable potential local export without adding migration logic. Suspend reads run safely off the main thread.

**Known trade-offs / when it strains.**
`fallbackToDestructiveMigration()` is used (acceptable for a consumer app, not for production data integrity). No delete cascade — orphaned rows are possible but mitigated by overwriting on refresh. Query compile-time validation is limited to simple SQL.

**How to apply it.**
```kotlin
@Dao
interface DealsDao {
  @Query("SELECT * FROM Deal WHERE storeID IS :storeId ORDER BY dealRating DESC")
  fun getPagingStoreDeals(storeId: Int): PagingSource<Int, Deal>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addDeals(vararg deals: Deal)
}
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/db/dao/DealsDao.kt
- domain/src/main/java/pm/bam/gamedeals/domain/db/DomainDatabase.kt
- domain/src/main/java/pm/bam/gamedeals/domain/models/Deal.kt

### Paging 3 `RemoteMediator` for Infinite Scroll

**Status:** established
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

## What we don't do

- **No multi-source merging in repositories.** Each repository has one source facade. **Why we avoid it:** merging two remote sources requires explicit conflict and ordering rules — adding it on demand keeps repositories simple.
- **No per-field TTL.** Cache expiry is per-entity, not per-field. **Why we avoid it:** field-level TTL is overkill for the bounded entity sets here.
- **No offline-first sync.** No local mutation queue, conflict resolution, or sync graph. Repositories assume connectivity for refresh; network failure halts the operation. **Why we avoid it:** the app is read-mostly; introducing offline writes would be a major architectural shift.
- **No streaming refresh.** No WebSockets or SSE — all fetches are explicit request-response. **Why we avoid it:** the source APIs don't push; polling is simpler.
- **No request deduplication.** Multiple simultaneous calls to a `refresh*()` will each fetch. **Why we avoid it:** the surface area where this matters has been small enough that the simpler approach wins.
- **No separate `LocalDataSource` / `RemoteDataSource` layers.** Repositories directly inject DAOs and source interfaces. **Why we avoid it:** the second indirection adds noise without buying anything for one-way refresh patterns.
