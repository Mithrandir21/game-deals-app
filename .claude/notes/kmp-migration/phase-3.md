# Phase 3 — Retrofit + Sandwich → Ktor + sandwich-ktor

**Branch:** `feature/kmp-migration-phase-3-remote`
**Sub-commits:**
- 3.1 — `:remote` KMP shell + engine factory + dual-aware exception transformer
- 3.2 — `:remote:gamerpower` Retrofit→Ktor (1 API)
- 3.3 — `:remote:cheapshark` Retrofit→Ktor (4 APIs)
- 3.4 — Cleanup: drop Retrofit/Sandwich-Retrofit, simplify transformer, move `RemoteExceptionTransformer` + `RemoteHttpException` to commonMain

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
