---
**Path scope:** `remote/**`, `domain/**`, `common/**`, `app/**`
**Last surveyed:** 34b01013 on 2026-05-18
---

# Resources

This codebase has a clean, minimal resource-lifecycle approach centered on structured concurrency and framework-managed cleanup. Explicit file/stream management is nearly absent in production code; Ktor `HttpClient` instances are singleton-scoped per vendor, and database writes that must be atomic use Room KMP's `useWriterConnection` + `immediateTransaction` pair.

## Patterns

### Room KMP Transactions via `useWriterConnection` + `immediateTransaction`

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all multi-step DB writes that must be atomic (cache-refresh clear-then-insert flows in DealsRepository, StoresRepository)

**The pattern.**
Write operations that must be atomic — most commonly the clear-then-insert sequence used during cache refresh — are wrapped in `database.useWriterConnection { transactor -> transactor.immediateTransaction { ... } }`. Room KMP's transaction API is coroutine-native: no manual `beginTransaction` / `endTransaction`, no explicit commit/rollback. Cancellation propagates through the suspending boundary; `CancellationException` rolls back the transaction rather than being swallowed. The outer `useWriterConnection` block scopes the writer connection explicitly; the inner `immediateTransaction` begins the transaction on that connection.

**Why this works for us.**
Same atomicity guarantees as the old `withTransaction { }` extension, with explicit writer-connection scoping that makes the writer boundary visible at the call site. Coroutine-safe and structured-concurrency-friendly. Lives in `commonMain` and works unchanged on Android and iOS — the project's Room KMP migration target.

**Known trade-offs / when it strains.**
More verbose than the old `withTransaction { }` one-liner; the two-level nesting (`useWriterConnection { transactor -> transactor.immediateTransaction { } }`) reads heavily for simple one-shot writes. No project-side helper yet collapses the nesting, so every call site spells the full shape. As before, transactions hold the writer connection for the duration of the block — keep network I/O outside the inner block where possible.

**How to apply it.**
```kotlin
database.useWriterConnection { transactor ->
  transactor.immediateTransaction {
    dealsDao.clearDealsForStore(storeId)
    val refreshed = cheapsharkSource.fetchDealsForStore(storeId)
      .map { it.copy(expires = expiresAt) }
    dealsDao.addDeals(*refreshed.toTypedArray())
  }
}
```

**Seen in.**
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/repositories/stores/StoresRepository.kt

**Related lessons.** L-2026-05-15-06

**Tags.** `room`, `transactions`, `kmp`

### `BufferedReader.use { }` for Short-Lived Streams

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** instrumented test fixture loader

**The pattern.**
Instrumentation test fixture loader explicitly closes buffered asset streams via Kotlin's `.use { }` block. The reader is closed automatically after the block, even if `readText()` throws.

**Why this works for us.**
`.use { }` is the idiomatic Kotlin equivalent of try-with-resources. Asset streams should not be held open across request boundaries in tests.

**Known trade-offs / when it strains.**
Currently appears only in test code; production code reads no files directly. If production stream I/O is added, the same pattern applies.

**How to apply it.**
```kotlin
val body = InstrumentationRegistry.getInstrumentation().context.assets
  .open("fixtures/$fixture")
  .bufferedReader()
  .use { it.readText() }
```

**Seen in.**
- app/src/androidTest/java/pm/bam/gamedeals/integration/support/FixtureRequestHandler.kt

### Singleton Ktor `HttpClient` per Vendor (Koin-bound)

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all HTTP calls (CheapShark and GamerPower APIs)

**The pattern.**
Each vendor (CheapShark, GamerPower) gets its own `HttpClient` singleton, bound in Koin under a `named()` qualifier (`CHEAPSHARK_QUALIFIER`, `GAMERPOWER_QUALIFIER`). Configuration is identical per vendor but namespaced: `ContentNegotiation` with kotlinx.serialization JSON, `HttpTimeout`, and `expectSuccess = true`. Ktor manages connection pooling and idle eviction internally — no manual pool config at the call site, and no explicit `close()` on response bodies. The platform engine is injected via expect/actual factories (OkHttp on Android, Darwin on iOS); the boundary mechanics live in `kmp.md`. Lifecycle is application-singleton: Koin holds the only reference to each `HttpClient`, so there is no explicit `close()` call site.

**Why this works for us.**
A single `HttpClient` per vendor preserves the "one pool per source" property the previous OkHttp setup had, while moving the wire layer into `commonMain`. Configuration is centralized — JSON, timeouts, and error semantics are declared once per vendor. The expect/actual engine seam keeps platform code (OkHttp config, Darwin config) at the edges instead of leaking into call sites.

**Known trade-offs / when it strains.**
Two `HttpClient` instances run concurrently even though OkHttp on Android can multiplex requests across vendors via a shared dispatcher; the project values per-vendor isolation (independent timeouts, independent failure modes) over that micro-optimization. Each new vendor adds another singleton — fine at two, worth reconsidering at four or five.

**How to apply it.**
```kotlin
val cheapsharkRemoteModule = module {
  single(CHEAPSHARK_QUALIFIER) {                 // CHEAPSHARK_QUALIFIER = named("cheapshark")
    HttpClient(get<HttpClientEngine>()) {
      expectSuccess = true
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
      install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    }
  }
}
```

**Seen in.**
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/di/RemoteNetworkModule.kt
- remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/di/RemoteNetworkModule.kt

**Tags.** `ktor`, `http`, `kmp`, `lifecycle`

## What we don't do

- **No raw SQLite cursors.** Room DAOs handle all reads via suspend functions or Flows. **Why we avoid it:** Room's generated code already handles cursor lifecycle correctly; raw cursors would re-introduce a leaking-cursor risk.
- **No file I/O outside tests.** No `InputStream` / `OutputStream` in production; data flows via Ktor + Room KMP. **Why we avoid it:** there's no production need; if added, prefer `.use { }` to guarantee closure.
- **No manual image-loading lifecycle.** Coil is configured as a singleton `ImageLoader` in DI; loading handles and cancellation are managed by Compose's `AsyncImage` and Coil internally.
- **No Java try-with-resources.** Kotlin's `.use { }` is the idiomatic pattern.
- **No `ContentProviderClient` usage.** Not present in this codebase.
- **No manual `HttpClient.close()` at call sites.** `HttpClient` is a Koin-held singleton for the lifetime of the app; closing it would invalidate the pool for every caller.

## Decommissioned

### Room Transactions via `withTransaction { }`

**Status:** deprecated (Room KMP changed the transaction API; superseded by useWriterConnection + immediateTransaction — see Room KMP Transactions entry)
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all multi-step DB writes (DealsRepository, GamesRepository, DealsMediator)

**The pattern.**
Room's `withTransaction { }` extension wraps multi-step write sequences in a transactional block. The transaction is scoped to the block; commit/rollback is automatic. Cancellation propagates naturally — `CancellationException` rolls back, never gets swallowed.

**Why this works for us.**
Room's coroutine-aware transaction integrates with structured concurrency. No manual `beginTransaction` / `endTransaction`. Atomicity for clear-then-insert patterns (paged refresh) is guaranteed.

**Known trade-offs / when it strains.**
Transactions block the database writer thread for the duration of the block. Heavy I/O inside (e.g., a non-paginated network fetch) could briefly stall other queries. In practice, network calls are already wrapped in `withTransaction`, so the latency is acceptable.

**How to apply it.**
```kotlin
domainDatabase.withTransaction {
  dealsDao.clearDealsForStore(storeId)
  cheapsharkSource.fetchDealsForStore(...)
    .map { it.copy(expires = expiresAt) }
    .let { dealsDao.addDeals(*it.toTypedArray()) }
}
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt
- domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/paging/DealsMediator.kt

### Singleton `OkHttpClient` per Vendor with Connection Pooling

**Status:** deprecated (Retrofit + OkHttp replaced by Ktor 2026-05-17; OkHttp survives only as the Android Ktor engine — see Singleton Ktor HttpClient entry)
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all HTTP calls (CheapShark and GamerPower APIs)

**The pattern.**
`OkHttpClient` is created once per API source (CheapShark, GamerPower) as a `@Singleton` Hilt provision and injected into Retrofit. Connection pooling and idle connection cleanup are automatic; response bodies are implicitly closed by Retrofit's adapter (Sandwich) — no manual `Response.close()` at call sites.

**Why this works for us.**
A single client per source maintains a single connection pool, reusing sockets across requests. Pool eviction handles idle connections automatically.

**Known trade-offs / when it strains.**
Two singletons (CheapShark + GamerPower) means two separate pools. For a two-source app, negligible. If a fourth or fifth API is added, consider whether they can share a client.

**How to apply it.**
```kotlin
@Provides
@Singleton
@CheapShark
fun provideOkHttpClient(remoteBuildUtil: RemoteBuildUtil): OkHttpClient =
  OkHttpClient.Builder()
    .apply {
      if (remoteBuildUtil.buildType() == RemoteBuildType.DEBUG) {
        addInterceptor(HttpLoggingInterceptor().apply { level = BODY })
      }
    }
    .connectTimeout(10, TimeUnit.SECONDS)
    .build()
```

**Seen in.**
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/di/RemoteNetworkModule.kt
- remote/gamerpower/src/main/java/pm/bam/gamedeals/remote/gamerpower/di/RemoteNetworkModule.kt
