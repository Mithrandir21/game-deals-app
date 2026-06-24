---
**Path scope:** `*/src/commonMain/kotlin/**/ui/**/*.kt`, `*/src/androidDeviceTest/**/*Test.kt`, `app/src/androidTest/**/*Test.kt`
**Last surveyed:** 34b01013 on 2026-05-18
---

# UI Testing — Compose Node Finders

This codebase finds Compose nodes the way users (and TalkBack) experience them: by visible text, by content description, and by accessibility role. `testTag` is forbidden — both as a modifier on production composables and as a finder in tests. The same `Modifier.semantics { contentDescription = … }` and `clickable(role = Role.Button)` that disambiguate a node for a test also improve TalkBack output for real users, so the production code changes are real accessibility improvements rather than test plumbing.

This file documents the **policy** (the finder hierarchy and the production-side conventions that support it). For the broader unit-test toolchain — `MainDispatcherTest`, Mokkery, `MockHttpClient`, Koin test-override modules — see [testing.md](testing.md).

## Patterns

### Find by Visible Text, Then Content Description, Then Role

**Status:** established
**First documented:** 2026-05-14
**Last verified:** 2026-05-18 @ 34b01013
**Coverage:** instrumented tests in `:feature:game`, `:feature:search`, `:feature:store`, `:feature:giveaways`, `:feature:home`, `:common:ui`, and the `:app` journey test

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
- feature/store/src/androidDeviceTest/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreenTest.kt and common/ui/src/androidDeviceTest/kotlin/pm/bam/gamedeals/common/ui/deal/DealBottomSheetTest.kt (`hasRole` helper + `hasContentDescription` combined matcher)
- app/src/androidTest/java/pm/bam/gamedeals/integration/HomeToStoreToDealJourneyTest.kt (cross-screen integration uses `onNodeWithText` and `onNodeWithContentDescription` with `substring = true`)

### Per-Test-Class `ScreenSemantics` + `setupCompose`

**Status:** established
**First documented:** 2026-05-14
**Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every instrumented `*ScreenTest.kt` that asserts against a `stringResource`

**The pattern.**
Each test class declares two pieces of structure:

1. A private nested `data class ScreenSemantics(...)` holding every string resource the suite consumes as `val` fields, plus a `@Composable fun load(): ScreenSemantics` companion factory that resolves each `stringResource(Res.string.X)` once. Parameterised CDs (anything formatted with runtime data, like `deal.title` or a store name) live alongside as `@Composable` companion methods: `fun dealRowCd(title: String, price: String): String`.
2. A private `setupCompose(...)` function wrapping `composeTestRule.setContent { GameDealsTheme { Screen(...) } }`. It assigns `screenSemantics = ScreenSemantics.load()` inside the `setContent` lambda (so the resources are captured during composition) and takes default-valued parameters for any per-test callback overrides (`onBack`, `onClick`, etc.).

A class-level `lateinit var screenSemantics: ScreenSemantics` holds the captured object after `setupCompose()` returns. Tests then read `screenSemantics.loading`, `screenSemantics.retry`, etc., directly in assertions — no per-test `var x = ""` declarations and no inlined `setContent` blocks.

**Why this works for us.**
Resources are the source of truth for user-visible copy; hardcoded literals drift on every translation update. Bundling captures into one `ScreenSemantics.load()` keeps every string the test class depends on in one auditable place. The `setupCompose` extraction removes the ~10 lines of identical `setContent { GameDealsTheme { Screen(...) } }` boilerplate that every test would otherwise repeat. Test bodies shrink to mock-stub setup + `setupCompose()` + assertions, which reads as arrange/act/assert.

**Known trade-offs / when it strains.**
- `lateinit var screenSemantics` will throw `UninitializedPropertyAccessException` if a test forgets `setupCompose()` — that's noisier than the pre-refactor `var = ""` form, but the failure is also clearer.
- Parameterised CDs can't be `val` fields. The `@Composable` method form on the companion keeps them inside the same data class but requires the test to capture the result into a class-level `var` (assigned inside `setupCompose`). Don't try to fold them back into `ScreenSemantics.load()`'s constructor — that couples the semantics object to fixture data.
- `setupCompose` accumulates parameters as the screen's signature grows. Default values for every callback keep call-sites short, but at some point a builder or a per-test `setContent { ... }` escape hatch may be cleaner.
- MockK stubs that the screen reads on first composition must be set up **before** `setupCompose()` is called. Same constraint as today's pattern, just less obvious because `setContent` is hidden behind the helper.

**When to apply.**
Any new Compose UI test class with ≥2 string-resource lookups or ≥2 `setContent` blocks. For a single-test, single-string class the inline `var = ""` form is still fine, but every existing in-repo `*ScreenTest.kt` already qualifies.

**How to apply it.**
```kotlin
class GiveawaysScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: GiveawaysViewModel = mockk()
    private lateinit var screenSemantics: ScreenSemantics

    private fun setupCompose(
        onBack: () -> Unit = {},
        goToWeb: (String, String) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            GameDealsTheme {
                GiveawaysScreen(onBack = onBack, goToWeb = goToWeb, viewModel = viewModel)
            }
        }
    }

    @Test
    fun errorState() {
        every { viewModel.uiState } returns MutableStateFlow(errorState)
        every { viewModel.reloadGiveaways() } just Runs

        setupCompose()

        composeTestRule.onNodeWithText(screenSemantics.errorMsg).assertIsDisplayed()
        composeTestRule.onNodeWithText(screenSemantics.retry).performClick()

        verify(exactly = 1) { viewModel.reloadGiveaways() }
    }

    private data class ScreenSemantics(
        val loading: String,
        val errorMsg: String,
        val retry: String,
    ) {
        companion object {
            @Composable
            fun load(): ScreenSemantics = ScreenSemantics(
                loading = stringResource(Res.string.giveaway_screen_loading_indicator),
                errorMsg = stringResource(Res.string.giveaway_screen_data_loading_error_msg),
                retry = stringResource(Res.string.giveaway_screen_data_loading_error_retry),
            )
        }
    }
}
```

Parameterised CDs use a `@Composable` companion method and a class-level `var` captured inside `setupCompose`:
```kotlin
private var dealRowCd: String = ""

private fun setupCompose(/* … */) {
    composeTestRule.setContent {
        screenSemantics = ScreenSemantics.load()
        dealRowCd = ScreenSemantics.dealRowCd(deal.title, deal.salePriceDenominated)
        GameDealsTheme { StoreScreen(...) }
    }
}

private data class ScreenSemantics(val back: String) {
    companion object {
        @Composable fun load(): ScreenSemantics =
            ScreenSemantics(back = stringResource(Res.string.store_screen_navigation_back_icon))

        @Composable fun dealRowCd(title: String, price: String): String =
            stringResource(Res.string.store_screen_deal_row_description, title, price)
    }
}
```

**Seen in.**
- feature/giveaways/src/androidDeviceTest/kotlin/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysScreenTest.kt
- feature/game/src/androidDeviceTest/kotlin/pm/bam/gamedeals/feature/game/ui/GameScreenTest.kt
- feature/search/src/androidDeviceTest/kotlin/pm/bam/gamedeals/feature/search/ui/SearchScreenTest.kt
- feature/store/src/androidDeviceTest/kotlin/pm/bam/gamedeals/feature/store/ui/StoreScreenTest.kt (parameterised CD helper)
- feature/home/src/androidDeviceTest/kotlin/pm/bam/gamedeals/feature/home/ui/HomeScreenTest.kt (parameterised CD helpers)
- common/ui/src/androidDeviceTest/kotlin/pm/bam/gamedeals/common/ui/deal/DealBottomSheetTest.kt (parameterised CD helper)

**Exception**: WebViewTest at `feature/webview/src/androidDeviceTest/.../WebViewTest.kt` uses hardcoded CD strings instead of `ScreenSemantics` and `createAndroidComposeRule<ComponentActivity>()` instead of `createComposeRule()`. This is intentional — the composable is activity-bound (needs `LocalUriHandler`) and has fewer than 5 assertions without parameterised CDs, so the ScreenSemantics overhead isn't worth it.

### `clickable(role = Role.Button)` on Tap-Responsive Surfaces

**Status:** established
**First documented:** 2026-05-14
**Last verified:** 2026-05-18 @ 34b01013
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
**Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all six refactored `*ScreenTest.kt` files

**The pattern.**
Per-feature `*ScreenTest.kt` files keep their assertions inline rather than wrapping each lookup in a named extension function. `composeTestRule.onNodeWithContentDescription(cd).assertIsDisplayed()` is clearer at the call-site than `composeTestRule.assertLoadingShown(cd)` when there is one consumer. Extension-function helpers (`tapBackButton()`, `assertDealRowShown(title)`) only earn their keep when **two or more** test files share the same screen pattern — typically when an integration test in `:app` exercises the same surface as a screen-level test in `:feature:*`. The per-class `setupCompose()` extraction documented above is the one helper that does belong inside a single test class, because it consolidates boilerplate that every test in that class needs.

**Why this works for us.**
A helper that takes resolved CDs as parameters re-introduces ceremony at the call-site, defeating its readability win. Inline `onNodeWith*` chains read directly: a reader doesn't have to jump to a helpers file to understand what is being asserted. Helpers also obscure the test's coupling to specific resources; inlining makes each lookup auditable in place.

**Known trade-offs / when it strains.**
When the same screen surface is exercised by multiple test files (e.g., `:feature:store`'s `StoreScreenTest` and `:app`'s `HomeToStoreToDealJourneyTest` both tap a deal row), the lookup logic ends up duplicated. At that point the helper moves to a shared source set (a `testing-ui-android` module, or extensions under `:testing/src/androidMain`) — never alongside a single feature's tests.

**How to apply it.**
Inline (default — assumes the per-class `ScreenSemantics` + `setupCompose` from the pattern above):
```kotlin
@Test
fun onShowFilters() {
    every { viewModel.uiState } returns MutableStateFlow(loadingState)

    setupCompose()

    composeTestRule.onNodeWithContentDescription(screenSemantics.filtersIcon).performClick()
    composeTestRule.onNodeWithText(screenSemantics.platformLabel).assertIsDisplayed()
}
```

Promotion to a shared cross-class helper happens only when a second test file would import it.

**Seen in.** All six refactored test files in `:feature:game`, `:feature:search`, `:feature:store`, `:feature:giveaways`, `:feature:home`, and `:common:ui`.

## What we don't do

- **No `testTag` on production composables.** Removed wholesale during the May 2026 refactor; never added back. Tests find nodes by what users see, and `testTag` constants leak into other tests as hardcoded strings.
- **No `Modifier.semantics { contentDescription = … }` on wrapper `Column`/`Box`/`ModalBottomSheet`.** The wrapper's CD masks its children in the merged semantic tree and produces noisy TalkBack output. Assert on a known child of the panel instead.
- **No `@Composable` test helpers outside `ScreenSemantics`.** The `@Composable load()` factory and parameterised `@Composable` accessors live inside the per-class `ScreenSemantics` companion (so they're invoked inside `setContent`'s composable scope). Free-standing `@Composable` test helpers that would have to be called from `setContent` and somehow plumb their result back out are not used — the `ScreenSemantics` capture pattern covers the same need cleanly.
- **No `onNodeWithTag(...)` in `:app` or feature instrumented tests.** Same reasoning as the production-side rule. As of `2026-05-14` no production composable or instrumented test in this codebase carries a `testTag`; new ones must not introduce them.
