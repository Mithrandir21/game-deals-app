---
**Path scope:** `remote/**`, `domain/**`, `common/**`, `app/**`
**Last surveyed:** 31a89bc on 2026-05-03
---

# Resources

This codebase has a clean, minimal resource-lifecycle approach centered on structured concurrency and framework-managed cleanup. Explicit file/stream management is nearly absent in production code; OkHttp connections are singleton-scoped, and database transactions use Room's built-in `withTransaction` block.

## Patterns

### Room Transactions via `withTransaction { }`

**Status:** established
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

**Status:** established
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

### `BufferedReader.use { }` for Short-Lived Streams

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
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
- app/src/androidTest/java/pm/bam/gamedeals/integration/support/FixtureMockDispatcher.kt

## What we don't do

- **No raw SQLite cursors.** Room DAOs handle all reads via suspend functions or Flows. **Why we avoid it:** Room's generated code already handles cursor lifecycle correctly; raw cursors would re-introduce a leaking-cursor risk.
- **No file I/O outside tests.** No `InputStream` / `OutputStream` in production; data flows via Retrofit + Room. **Why we avoid it:** there's no production need; if added, prefer `.use { }` to guarantee closure.
- **No manual image-loading lifecycle.** Coil is configured as a singleton `ImageLoader` in DI; loading handles and cancellation are managed by Compose's `AsyncImage` and Coil internally.
- **No Java try-with-resources.** Kotlin's `.use { }` is the idiomatic pattern.
- **No `ContentProviderClient` usage.** Not present in this codebase.
