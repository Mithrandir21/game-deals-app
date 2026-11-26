> Note: Recommended fix follows L-2026-05-02-01 (use `MutableStateFlow.update { … }` for atomic field-level merges).

| Field | Value |
|---|---|
| Severity | Low |
| Category | Latent read-modify-write race |
| Location | `feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt:67-85` |
| Effort | Trivial |
| Confidence | Low |

**Description.** `searchGames` reads `searchParametersFlow.replayCache.firstOrNull()` and uses its fields as defaults (`title ?: current.title`, etc.) before re-emitting via `searchParametersFlow.emit(searchParameters)`. The launch wraps that as `viewModelScope.launch { val current = …; emit(...) }`. Two rapid invocations can both read `current` before either emits — analogous to the read-modify-write antipattern in `L-2026-05-02-01`.

**Impact.** Concurrent calls of the form `searchGames(title = "Foo")` then `searchGames(lowerPrice = 1000)` can race: both read the pre-merge value; the second emit overwrites the first's intended title with the prior (now stale) title. The flow is collected via `flatMapLatest`, so only the second value drives the search — the first's title contribution is dropped. The current single call site (SearchScreen.kt:106) supplies **all** parameters from `rememberSaveable` composable state, so the "keep prior" branch never runs in production. Latent against a future call site that forwards only one field at a time.

**Evidence.**
```kotlin
fun searchGames(title: String? = null, ...) {
    viewModelScope.launch {
        val current = searchParametersFlow.replayCache.firstOrNull() ?: SearchParameters()
        val searchParameters = SearchParameters(
            title = title ?: current.title,
            lowerPrice = lowerPrice ?: current.lowerPrice,
            ...
        )
        searchParametersFlow.emit(searchParameters)
    }
}
```

**Recommended fix.** Either (a) replace the SharedFlow with `MutableStateFlow<SearchParameters>` and use `.update { current -> SearchParameters(title = title ?: current.title, ...) }` (atomic by design), or (b) drop the per-field "keep prior" merge — require callers to supply the full snapshot.

**Confidence rationale.** Low — race is theoretically reachable but practically unreachable from the single existing call site. Recorded because the merge logic exists *for* the partial-fields case the API signature suggests.
