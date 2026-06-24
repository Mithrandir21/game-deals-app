| Field | Value |
|---|---|
| Severity | Low |
| Category | Latent state drift |
| Location | `feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt:352-354` |
| Effort | Small |
| Confidence | Low |

**Description.** Inside `Filters(existingSearchParameters: SearchParameters, …)`, three `rememberSaveable` slots derive their *initial* value from `existingSearchParameters`, but the parameter is not passed as a key. Once a slot is first composed, it persists the slider value across the `ModalBottomSheet`'s show→hide→show cycle. If a future code path mutates `existingSearchParameters` while the sheet is closed (e.g., a "Reset filters" affordance), reopening the sheet will *not* reflect the new initial.

**Impact.** No observable bug today — the only path that updates `existingSearchParameters` is the in-sheet sliders themselves. The bug only manifests if an external "Reset" or "Apply preset" feature is added.

**Evidence.**
```kotlin
var priceSliderValue by rememberSaveable(stateSaver = floatRangeSaver) { mutableStateOf(existingPriceRange) }
var steamSliderValue by rememberSaveable { mutableFloatStateOf(existingMin) }
var exactMatch by rememberSaveable { mutableStateOf(existingSearchParameters.exact ?: false) }
```

**Recommended fix.** Either accept current behavior (state-of-the-sheet persistence is the UX) and add a comment, or key the slot to invalidate on parent change:
```kotlin
var priceSliderValue by rememberSaveable(
    existingPriceRange,
    stateSaver = floatRangeSaver,
) { mutableStateOf(existingPriceRange) }
```

**Confidence rationale.** Low because the current behavior may be intentional UX. Flagged for product input rather than recommending a change unilaterally.
