---
**Path scope:** `*/src/commonMain/kotlin/**/ui/**/*.kt`, `*/src/androidInstrumentedTest/**/*Test.kt`, `app/src/androidTest/**/*Test.kt`
**Last surveyed:** f215235 on 2026-05-14
---

# UI Testing — Compose Node Finders

This codebase finds Compose nodes the way users (and TalkBack) experience them: by visible text, by content description, and by accessibility role. `testTag` is forbidden — both as a modifier on production composables and as a finder in tests. The same `Modifier.semantics { contentDescription = … }` and `clickable(role = Role.Button)` that disambiguate a node for a test also improve TalkBack output for real users, so the production code changes are real accessibility improvements rather than test plumbing.

This file documents the **policy** (the finder hierarchy and the production-side conventions that support it). For the broader unit-test toolchain — `MainCoroutineRule`, MockK, MockWebServer, fixture-driven Hilt — see [testing.md](testing.md).

## Patterns

### Find by Visible Text, Then Content Description, Then Role

**Status:** established
**First documented:** 2026-05-14
**Last verified:** 2026-05-14 @ f215235
**Coverage:** instrumented tests in `:feature:game`, `:feature:search`, `:feature:store`, `:feature:giveaways`, and the `:app` journey test

**The pattern.**
Compose UI tests select nodes via a deliberate escalation:

1. **Visible text.** `onNodeWithText("…")` for any element that already shows user-readable copy: titles, labels, button text, store names, error messages, empty/no-results states. If the text legitimately appears in `n` places (e.g., the game title rendered in both the top app bar and the details body), use `onAllNodesWithText("…").assertCountEquals(n)` — that's a more honest assertion than picking one and ignoring the other.

2. **Content description.** `onNodeWithContentDescription("…")` for icons, images, sliders, switches, and the loading spinner. When the element has no natural label, add `Modifier.semantics { contentDescription = stringResource(…) }` with a real localizable string. **Only on leaf or semantic-bearing nodes.** Never on wrapper `Column`/`Box`/`ModalBottomSheet` containers — Compose merges descendant semantics into the parent, so a wrapper CD masks its children and produces noisy TalkBack output ("Search filters. Platforms. PC selected. …"). Assert that a known child of the panel is visible instead.

3. **Role.** Card/Row/Box surfaces that respond to a tap declare an explicit role via `clickable(role = Role.Button, onClick = …)`. Tests then disambiguate with `onNode(hasContentDescription("…") and hasRole(Role.Button))`. Compose's testing API does not ship `hasRole` — define a 3-line helper alongside the test that needs it.

**Why this works for us.**
Tests find UI the same way users and assistive tech do, so refactors that rename internal symbols don't break tests; refactors that change user-visible behavior do — which is what we want. CDs added to satisfy a test are simultaneously real accessibility improvements. `testTag` constants used to leak into other tests as hardcoded strings (the journey test had `"StoreTopBar"` and `"DealRowabc123"` baked in by hand); this pattern eliminates that coupling.

**Known trade-offs / when it strains.**
- Loading spinners have no visible text. Either attach a CD to the indicator and query it, or in tests assert via the toolbar text in the loading state (e.g., `onNodeWithText("Loading…")`).
- Dynamic visible text (a slider label that renders `"0 - 50"`) is queryable directly, but the test then depends on the rendered string format. Acceptable when the format is stable and tested elsewhere.
- The escalation requires judgment: don't add a CD when a visible label already identifies the element from the test's perspective.

**How to apply it.**
Production code — actionable surfaces get an explicit role + CD where there is no natural label:
```kotlin
val dealRowCd = stringResource(
    Res.string.store_screen_deal_row_description,
    deal.title,
    deal.salePriceDenominated,
)
Row(
    modifier = Modifier
        .clickable(role = Role.Button) { onView(deal) }
        .semantics { contentDescription = dealRowCd },
) { /* children render the title, price, etc. */ }
```

Test — capture strings inside `setContent` (because `stringResource` is `@Composable`), assert afterwards:
```kotlin
var dealRowCd = ""
composeTestRule.setContent {
    dealRowCd = stringResource(
        Res.string.store_screen_deal_row_description,
        deal.title,
        deal.salePriceDenominated,
    )
    GameDealsTheme { StoreScreen(...) }
}

composeTestRule.onNode(hasContentDescription(dealRowCd) and hasRole(Role.Button))
    .assertIsDisplayed()
```

The `hasRole` helper lives in the test file (or a shared test source set once a second consumer appears):
```kotlin
private fun hasRole(role: Role): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
```

**Seen in.**
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/ui/GameScreen.kt (visible text only — no CDs added beyond what was already there)
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreen.kt (CDs on the loading spinner, both sliders, and the exact-match switch)
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreen.kt (`clickable(role = Role.Button)` + CD on the deal Card)
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysScreen.kt (CD on the loading spinner)
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeScreen.kt (`clickable(role = Role.Button)` on every clickable Row; CD on the FAB loading spinner; all other lookups via visible text or the existing image CDs)
- feature/favourites/src/commonMain/kotlin/pm/bam/gamedeals/feature/favourites/ui/FavouritesScreen.kt (`clickable(role = Role.Button)` on the ListItem; CD on the loading spinner)
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/DealBottomSheet.kt (`clickable(role = Role.Button)` + CD on the cheaper-store Row; CD on the loading spinner)
- feature/store/src/androidInstrumentedTest/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreenTest.kt and common/ui/src/androidInstrumentedTest/kotlin/pm/bam/gamedeals/common/ui/deal/DealBottomSheetTest.kt (`hasRole` helper + `hasContentDescription` combined matcher)
- app/src/androidTest/java/pm/bam/gamedeals/integration/HomeToStoreToDealJourneyTest.kt (cross-screen integration uses `onNodeWithText` and `onNodeWithContentDescription` with `substring = true`)

### Capture String Resources Inside `setContent` for Use in Assertions

**Status:** established
**First documented:** 2026-05-14
**Last verified:** 2026-05-14 @ f215235
**Coverage:** every instrumented `*ScreenTest.kt` that asserts against a `stringResource`

**The pattern.**
`stringResource(...)` is `@Composable`, so it can only be called inside a composable block. Tests that need to compare against a resource declare a `var` outside `setContent`, assign it inside the `setContent` lambda, and reference the captured value in the assertions below. For tests that need several resources, bundle the captures inside `setContent` and return a small data class from a private helper rather than scattering `var` declarations.

**Why this works for us.**
Resources are the source of truth for user-visible copy; hardcoded literals in tests drift from the production strings on every translation update. The capture pattern keeps tests reading directly from the same `Res.string.x` that the screen renders. Closing over a `var` from the `@Composable` lambda is supported and well-defined in Compose UI tests.

**Known trade-offs / when it strains.**
The `var x = ""` placeholder pattern is mildly ugly and forces every assertion to be ordered after the `setContent` block. For tests that exercise the same screen many times with the same strings, factor the captures into a private helper that returns them.

**How to apply it.**
```kotlin
@Test
fun errorState() {
    every { viewModel.uiState } returns MutableStateFlow(GameScreenData.Error)

    var snackText = ""
    var snackRetry = ""

    composeTestRule.setContent {
        snackText = stringResource(Res.string.game_screen_data_loading_error_msg)
        snackRetry = stringResource(Res.string.game_screen_data_loading_error_retry)

        GameDealsTheme {
            GameScreen(onBack = {}, goToWeb = { _, _ -> }, viewModel = viewModel)
        }
    }

    composeTestRule.onNodeWithText(snackText).assertIsDisplayed()
    composeTestRule.onNodeWithText(snackRetry).assertIsDisplayed().performClick()
}
```

Bundled variant for tests sharing a setup helper:
```kotlin
private data class FilterCds(val iconCd: String, val panelCd: String, val switchCd: String)

private fun openFilters(): FilterCds {
    var iconCd = ""; var panelCd = ""; var switchCd = ""
    composeTestRule.setContent {
        iconCd = stringResource(Res.string.search_screen_filters_icon)
        panelCd = stringResource(Res.string.search_screen_filters_panel_description)
        switchCd = stringResource(Res.string.search_screen_filters_exact_match_switch_description)
        GameDealsTheme { SearchScreen(...) }
    }
    return FilterCds(iconCd, panelCd, switchCd)
}
```

**Seen in.**
- feature/game/src/androidInstrumentedTest/kotlin/pm/bam/gamedeals/feature/game/ui/GameScreenTest.kt
- feature/search/src/androidInstrumentedTest/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreenTest.kt
- feature/store/src/androidInstrumentedTest/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreenTest.kt
- feature/giveaways/src/androidInstrumentedTest/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysScreenTest.kt

### `clickable(role = Role.Button)` on Tap-Responsive Surfaces

**Status:** established
**First documented:** 2026-05-14
**Last verified:** 2026-05-14 @ f215235
**Coverage:** any `Card`, `Row`, or `Box` that consumes a tap (i.e. `Modifier.clickable { … }`) and is not already a Material component with a built-in `Role.Button` (which `Button`, `IconButton`, and `FilterChip` already declare)

**The pattern.**
A composable that responds to a tap declares its accessibility role explicitly via the `role` parameter on `Modifier.clickable`: `Modifier.clickable(role = Role.Button) { onAction() }`. This is production code, not test plumbing — without it TalkBack reads the element as a generic clickable rather than as a button, and tests cannot disambiguate it from non-actionable siblings via `hasRole(Role.Button)`.

**Why this works for us.**
The role is a semantic property the platform exposes to TalkBack and Switch Access — getting it right matters for users. Combining `role = Role.Button` with a content description that explains the action ("Deal: Portal 2, $1.99") gives the test a precise matcher and gives screen-reader users a complete announcement.

**Known trade-offs / when it strains.**
Forgetting the role is silent: the production UI still works, TalkBack just degrades, and the test's `hasRole(Role.Button)` matcher silently finds zero nodes. The fix is to default to `clickable(role = Role.Button)` for any non-Material clickable in code review, and to write the test's combined matcher in the same change.

**How to apply it.**
```kotlin
Card {
    Row(
        modifier = Modifier
            .clickable(role = Role.Button) { onViewDealDetails(deal) }
            .padding(…)
            .semantics { contentDescription = dealRowCd },
    ) { … }
}
```

**Seen in.**
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreen.kt (`DealRow` card)

### Inline Assertions in Per-Feature Tests; Defer Helpers Until Shared

**Status:** established
**First documented:** 2026-05-14
**Last verified:** 2026-05-14 @ f215235
**Coverage:** all four refactored `*ScreenTest.kt` files

**The pattern.**
Per-feature `*ScreenTest.kt` files keep their assertions inline rather than wrapping each lookup in a named extension function. `composeTestRule.onNodeWithContentDescription(cd).assertIsDisplayed()` is clearer at the call-site than `composeTestRule.assertLoadingShown(cd)` when there is one consumer. Extension-function helpers (`tapBackButton()`, `assertDealRowShown(title)`) only earn their keep when **two or more** test files share the same screen pattern — typically when an integration test in `:app` exercises the same surface as a screen-level test in `:feature:*`.

**Why this works for us.**
A helper that takes resolved CDs as parameters re-introduces ceremony at the call-site, defeating its readability win. Inline `onNodeWith*` chains read directly: a reader doesn't have to jump to a helpers file to understand what is being asserted. Helpers also obscure the test's coupling to specific resources; inlining makes each lookup auditable in place.

**Known trade-offs / when it strains.**
When the same screen surface is exercised by multiple test files (e.g., `:feature:store`'s `StoreScreenTest` and `:app`'s `HomeToStoreToDealJourneyTest` both tap a deal row), the lookup logic ends up duplicated. At that point the helper moves to a shared source set (a `testing-ui-android` module, or extensions under `:testing/src/androidMain`) — never alongside a single feature's tests.

**How to apply it.**
Inline (default):
```kotlin
@Test
fun onShowFilters() {
    every { viewModel.uiState } returns MutableStateFlow(loadingState)

    var filtersIconCd = ""
    var filtersPanelChildCd = ""

    composeTestRule.setContent {
        filtersIconCd = stringResource(Res.string.giveaway_screen_filters_icon)
        filtersPanelChildCd = stringResource(Res.string.giveaway_screen_filters_platform_label)
        GameDealsTheme { GiveawaysScreen(...) }
    }

    composeTestRule.onNodeWithContentDescription(filtersIconCd).performClick()
    composeTestRule.onNodeWithText(filtersPanelChildCd).assertIsDisplayed()
}
```

Promotion to a shared helper happens only when a second test file would import it.

**Seen in.** All four refactored test files in `:feature:game`, `:feature:search`, `:feature:store`, `:feature:giveaways`.

## What we don't do

- **No `testTag` on production composables.** Removed wholesale during the May 2026 refactor; never added back. Tests find nodes by what users see, and `testTag` constants leak into other tests as hardcoded strings.
- **No `Modifier.semantics { contentDescription = … }` on wrapper `Column`/`Box`/`ModalBottomSheet`.** The wrapper's CD masks its children in the merged semantic tree and produces noisy TalkBack output. Assert on a known child of the panel instead.
- **No `@Composable` test helpers.** Helpers can't call `stringResource(...)` unless they're themselves `@Composable`, which would force them to run inside `setContent`. The capture-into-`var` pattern is preferred.
- **No `onNodeWithTag(...)` in `:app` or feature instrumented tests.** Same reasoning as the production-side rule. As of `2026-05-14` no production composable or instrumented test in this codebase carries a `testTag`; new ones must not introduce them.
