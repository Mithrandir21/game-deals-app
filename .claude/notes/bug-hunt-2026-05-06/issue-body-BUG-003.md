> Note: Closed #46 also addressed a `.catch` issue in `StoreViewModel`, but on a *different* Flow chain (the Paging `pagedDeals` `cachedIn` chain). This finding is on the sibling `deals` StateFlow chain, which has the opposite problem: a missing terminal `.catch`. Fixing this does not regress #46.

| Field | Value |
|---|---|
| Severity | Medium |
| Category | Flow tombstone / unrecoverable error state |
| Location | `feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt:63-74` |
| Effort | Trivial |
| Confidence | Medium |

**Description.** `StoreViewModel.deals` is built as `storeIdFlow → flatMapLatest { dealsRepository.observeStoreDeals(it) } → map → logFlow → stateIn(WhileSubscribed(5000), persistentListOf())`. The upstream `observeStoreDeals` does `dealsDao.observeStoreDeals(storeId).onStart { refreshDeals(storeId) }` (DealsRepository.kt:49-51), so a network failure inside `refreshDeals` propagates up the Flow before any DB emission. The chain has no `.catch` (compare to the sibling `uiState`, which does), so the `stateIn` upstream collector terminates exceptionally.

**Impact.** While at least one subscriber is active, the upstream is alive. When `refreshDeals` throws (no network, server 5xx mapped through `RemoteHttpException`), the upstream completes exceptionally; the `stateIn` collector dies; existing subscribers never see new deals. The user keeps seeing `persistentListOf()` even after connectivity returns. Recovery only happens if all subscribers leave for ≥5 s and a new subscriber arrives, re-triggering the upstream. The sibling `uiState` *does* have `.catch { emit(StoreScreenData.Error) }`, so the screen header shows an error — but the deals list rendered from `viewModel.deals` (StoreScreen.kt:78) silently stays empty when the user hits "retry".

**Evidence.**
```kotlin
val deals: StateFlow<ImmutableList<Deal>> = storeIdFlow
    .filterNotNull()
    .distinctUntilChanged()
    .flatMapLatest { dealsRepository.observeStoreDeals(it) }
    .map { it.toImmutableList() }
    .logFlow(logger)             // re-throws via .onError
    .stateIn(...)                // no `.catch` upstream — collector dies on first throw
```

**Recommended fix.** Add `.catch { emit(persistentListOf()) }` immediately above `stateIn`:
```kotlin
.map { it.toImmutableList() }
.logFlow(logger)
.catch { emit(persistentListOf()) }
.stateIn(...)
```

**Confidence rationale.** Medium — the failure mode requires the network refresh to throw before any DB emission; pre-existing cache may mask this on a warm cache. Asymmetry with the sibling `uiState` flow (which catches) and with `StoreScreen`'s retry UX (which recovers `uiState` without re-priming `deals`) make this a real regression vector. Not High because the end-to-end exception path through Ktor + sandwich + RemoteExceptionTransformer wasn't traced.
