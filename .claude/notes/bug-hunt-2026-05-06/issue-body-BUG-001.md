> Note: Same antipattern as previously fixed in #71 (`DealDetailsController`) and #31 (`DealsMediator`). Per L-2026-05-02-04, every `catch (Throwable)` block wrapping suspending work in this codebase rethrows `CancellationException` first. This is the only outlier remaining.

| Field | Value |
|---|---|
| Severity | High |
| Category | Cancellation swallow / non-cooperative cancellation |
| Location | `feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt:66-76` |
| Effort | Trivial |
| Confidence | High |

**Description.** `reloadGiveaways` launches a coroutine that calls the suspending `giveawaysRepository.refreshGiveaways()` and wraps it in `catch (_: Throwable)`, which also matches `CancellationException`. This is the exact antipattern called out in lesson `L-2026-05-02-04`. All other `catch (Throwable)` blocks in the codebase that wrap suspending work (DealsApi, GamesApi, StoresApi, ReleaseApi, gamerpower GamesApi, DealDetailsController) already rethrow `CancellationException` first; this is the only outlier.

**Impact.** When the ViewModel is cleared (or any ancestor scope is cancelled) while `refreshGiveaways()` is suspended, the `CancellationException` is silently dropped. The catch body then writes `refreshOutcomeFlow.value = RefreshOutcome.Error`, so a cancelled reload looks like a refresh failure. Combined with `loadingFlow.value = true` set above (only cleared by a subsequent Room emission), the surviving StateFlow can retain an `Error` reading set by a cancelled reload.

**Evidence.**
```kotlin
fun reloadGiveaways() {
    viewModelScope.launch {
        loadingFlow.value = true
        refreshOutcomeFlow.value = RefreshOutcome.Idle
        try {
            giveawaysRepository.refreshGiveaways()
        } catch (_: Throwable) {                              // swallows CE
            refreshOutcomeFlow.value = RefreshOutcome.Error
        }
    }
}
```

**Recommended fix.** Catch `CancellationException` explicitly first and rethrow:
```kotlin
try {
    giveawaysRepository.refreshGiveaways()
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    refreshOutcomeFlow.value = RefreshOutcome.Error
}
```

**Confidence rationale.** High — exact antipattern with a confirmed lesson; call site unambiguously wraps a `suspend` function. Trivial single-line fix.
