# Phase 3 — Retrofit + Sandwich → Ktor + sandwich-ktor

**Branch:** `feature/kmp-migration-phase-3-remote`
**Sub-commits:**
- 3.1 — `:remote` KMP shell + engine factory + dual-aware exception transformer
- 3.2 — `:remote:gamerpower` Retrofit→Ktor (1 API)
- 3.3 — `:remote:cheapshark` Retrofit→Ktor (4 APIs)
- 3.4 — Cleanup: drop Retrofit/Sandwich-Retrofit, simplify transformer, move `RemoteExceptionTransformer` + `RemoteHttpException` to commonMain
- 3.5 — Post-build runtime fixes: route Ktor `Logging` to Logcat (`KtorLogcatLogger`); drop `LogLevel.BODY` to `LogLevel.HEADERS`; add `requestTimeoutMillis = 30_000` defensive setting

## What was done

### Tech swaps
- Retrofit + sandwich-retrofit → Ktor Client + sandwich-ktor.
- HttpClient engine: `expect/actual` factory in `:remote` (OkHttp on Android, Darwin on iOS) — `internal expect fun httpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient`.
- ApiResponse wrapping: hand-rolled try/catch (sandwich's `responseOf` isn't suspend-aware in 2.x). `CancellationException` rethrown.
- HTTP error model: `expectSuccess = true` makes Ktor throw `ResponseException` on 4xx/5xx; the transformer maps that to `RemoteHttpException`.
- Tests: `MockWebServer` → Ktor `MockEngine`. JSON fixtures preserved verbatim; only the transport changes.

### Module shape after Phase 3
- `:remote` parent — KMP module. `commonMain` holds the engine `expect`, `RemoteExceptionTransformer` interface, `RemoteHttpException` sealed class, and the int-keyed code mapping. `androidMain` holds `RemoteExceptionTransformerImpl` (`@Inject`), `RemoteBuildUtil` (`BuildConfig`-coupled), and the sandwich `mapAnyFailure` / `log` extensions. `iosMain` has the Darwin engine actual.
- `:remote:cheapshark` — KMP module. 4 API classes (`DealsApi`, `GamesApi`, `ReleaseApi`, `StoresApi`), 4 `Remote*` models, `RemoteDealsQuery`, `RemoteDealsSortBy`, `ErrorResponse`, `CurrencyTransformation` interface, `InternalUtils.toBooleanStrict()` all live in `commonMain`. `androidMain` holds the Hilt DI, `CurrencyTransformationImpl` (uses `@Inject` + `String.format`), the source impl, and the mappers (which depend on `:domain`).
- `:remote:gamerpower` — KMP module. `GamesApi` and `RemoteGiveaway` live in `commonMain`. Same androidMain shape as cheapshark.

### Notable decisions
- **`Failure.Error` removed from `mapAnyFailure`.** That branch only fired in the sandwich-retrofit world (HTTP 4xx/5xx without an exception). Under Ktor + `expectSuccess = true` + try/catch, every failure is `Failure.Exception`. Dead branch deleted.
- **Sandwich's `responseOf` is not suspend-aware in 2.x.** Each API method has the same try/catch shape. Library-agnostic; survives sandwich/Ktor upgrades.
- **`RemoteDealsSortBy` enum query encoding.** Retrofit + kotlinx-serialization-converter previously emitted `@SerialName` values (e.g. `"DealRating"`). Ktor's `parameter()` calls `toString()` (returns `"DEALRATING"`). Hardcoded `RemoteDealsSortBy.toApiString()` mapping in `DealsApi.kt` keeps the wire format identical to the Retrofit era. Worth verifying with a manual smoke test that the sort filters still work.
- **`RemoteExceptionTransformerImplTest` rewrite.** The old test mocked Retrofit's `HttpException` (had a `code()` method, easy to mock). Ktor's `ResponseException` has a non-public constructor and wraps an `HttpResponse`. Cheapest way to obtain a real one is to let Ktor produce one naturally via `MockEngine` returning an error status while `expectSuccess = true`. The test file does this.
- **iOS targets only `compileKotlinIos*` is verified, not full link.** The framework itself isn't built/linked because nothing yet exports it (Phase 6's job). Compile coverage is enough to prove the source compiles for K/N.

### Post-build runtime fixes (3.5)
The build was green and the unit tests passed at the end of 3.4, but the app silently fetched no data on first device run. Three sequential gotchas, all Ktor-on-Android specific:

1. **No networking logs in Logcat at all.** Ktor's `Logger.DEFAULT` is SLF4J-based; on Android with no SLF4J binding it writes nowhere. Fixed with `KtorLogcatLogger` — a tiny `Logger` impl that hands every line to `android.util.Log.d("Ktor", ...)`. Wired into both `RemoteNetworkModule`s (commit `5198c1b`).
2. **`body<T>()` hung indefinitely.** REQUEST log fired, no RESPONSE, no exception, no timeout. Cause: Ktor's `Logging` plugin at `LogLevel.BODY` reads the response body to log it; on the OkHttp engine that body is a one-shot stream. `ContentNegotiation` then waits forever for bytes that have already been consumed. Fixed by dropping to `LogLevel.HEADERS` (commit `eb0a4be`). Captured as `L-2026-05-03-02` in `.claude/lessons.md`.
3. **`requestTimeoutMillis` was unset.** Ktor's `HttpTimeout` plugin doesn't set a default request timeout — only `connectTimeoutMillis` was configured (10s). After connection, reads could hang forever. Added `requestTimeoutMillis = 30_000` as a defensive setting (commit `b2c4aa3`). Wasn't load-bearing for the actual bug (#2 was) but is the right defensive shape now that we're on Ktor.

A misdirection along the way: an early hypothesis (commit `2fad740`) blamed a `RemoteStore.storeID` Int-vs-String type mismatch — cheapshark returns `"storeID": "1"` as a JSON string. The fix would have been wrong (the storeID type mismatch hadn't actually been reached because `body<T>()` never returned). Reverted in `8440d14` after the user used breakpoints to confirm the call hangs at `body<>()`. Worth knowing for future debugging: **a hung `body<T>()` masks every downstream issue**, so resolve the hang before chasing parser-shaped hypotheses.

## Build verification

| Task | Result |
|---|---|
| `:remote:assembleDebug` + `:test` + iOS sim compile | ✅ |
| `:remote:cheapshark:assembleDebug` + `:test` + iOS sim compile | ✅ |
| `:remote:gamerpower:assembleDebug` + `:test` + iOS sim compile | ✅ |
| `:app:assembleDebug` (whole project) | ✅ |
| `./gradlew test` (whole project) | ✅ |

## Lessons (candidates for `.claude/lessons.md`)

- **`expectSuccess = true` collapses the sandwich Failure axes.** Once enabled, the only failure shape is `Failure.Exception` — the `Failure.Error` branch becomes dead code. Worth removing it post-migration; otherwise reviewers wonder when it can fire.
- **Ktor's `parameter()` calls `toString()`, not the kotlinx-serialization `@SerialName`.** Enum query parameters that previously used SerialName-encoded values (Retrofit + kotlinx-serialization-converter behavior) need an explicit conversion function, otherwise the wire format changes silently and the API rejects the request.
- **`MockEngine` makes obtaining real Ktor exception types easy.** When testing exception-mapping code that needs a real `ResponseException`, don't try to mock it — let `MockEngine` + `expectSuccess = true` produce a genuine one. The test stays decoupled from Ktor's internal types.
- **Sandwich-ktor 2.x doesn't expose a suspend `responseOf`.** The ergonomic helper for wrapping suspend Ktor calls in `ApiResponse<T>` is missing. Hand-rolled try/catch with `CancellationException` rethrow is the workaround. If sandwich adds `suspendResponseOf` in a later version, the API class bodies can shrink.

## Next phase

Phase 4 — DI + observability swap. Hilt → Koin (big-bang across every module that currently uses Hilt). Firebase Crashlytics + Performance → Sentry. Firebase Analytics dropped. Most disruptive phase; Android will be unbuildable for hours-to-days inside it. Cut tag `kmp-pre-phase-4` before starting.
