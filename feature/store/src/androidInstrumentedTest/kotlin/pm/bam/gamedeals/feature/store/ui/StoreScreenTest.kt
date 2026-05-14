package pm.bam.gamedeals.feature.store.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.store.generated.resources.Res
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_deal_row_description
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_navigation_back_icon
import pm.bam.gamedeals.feature.store.ui.StoreViewModel.StoreScreenData

class StoreScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val deal: Deal = mockk()

    private val viewModel: StoreViewModel = mockk()

    private val storeId = 1

    private lateinit var screenSemantics: ScreenSemantics

    private var dealRowCd: String = ""

    @Before
    fun setup() {
        val dealId = "DealId"
        val dealTitle = "Title"
        val dealPrice = "Price"
        val dealThumb = "DealThumbnail"
        every { deal.dealID } returns dealId
        every { deal.storeID } returns storeId
        every { deal.gameID } returns 1
        every { deal.title } returns dealTitle
        every { deal.salePriceDenominated } returns dealPrice
        every { deal.thumb } returns dealThumb
        val deals: StateFlow<ImmutableList<Deal>> = MutableStateFlow(persistentListOf(deal))
        val dealDetails: StateFlow<DealBottomSheetData?> = MutableStateFlow(null)
        val uiState: StateFlow<StoreScreenData> = MutableStateFlow(StoreScreenData.Loading)
        val favouriteIds: StateFlow<Set<Int>> = MutableStateFlow(emptySet())

        every { viewModel.deals } returns deals
        every { viewModel.dealDetails } returns dealDetails
        every { viewModel.uiState } returns uiState
        every { viewModel.favouriteIds } returns favouriteIds
        every { viewModel.events } returns MutableSharedFlow<StoreViewModel.StoreUiEvent>().asSharedFlow()
    }

    private fun setupCompose(
        onBack: () -> Unit = {},
        goToWeb: (String, String) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            screenSemantics = ScreenSemantics.load()
            dealRowCd = ScreenSemantics.dealRowCd(deal.title, deal.salePriceDenominated)
            GameDealsTheme {
                StoreScreen(
                    onBack = onBack,
                    goToWeb = goToWeb,
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun loadSingleDeal() {
        setupCompose()

        composeTestRule.onNode(hasContentDescription(dealRowCd) and hasRole(Role.Button))
            .assertIsDisplayed()

        verify(exactly = 1) { viewModel.deals }
        verify(exactly = 1) { viewModel.dealDetails }
        verify(exactly = 1) { viewModel.uiState }
    }

    @Test
    fun loadDealDetails() {
        every { viewModel.loadDealDetails(any(), any(), any(), any(), any()) } just runs

        setupCompose()

        composeTestRule.onNode(hasContentDescription(dealRowCd) and hasRole(Role.Button))
            .performClick()

        verify(exactly = 1) { viewModel.deals }
        verify(exactly = 1) { viewModel.dealDetails }
        verify(exactly = 1) { viewModel.uiState }
        verify(exactly = 1) { viewModel.loadDealDetails(any(), any(), any(), any(), any()) }
    }

    @Test
    fun loadStoreDetails() {
        val name = "StoreName"
        val store: Store = mockk {
            every { images } returns mockk {
                every { banner } returns "Store Banner"
            }
            every { storeName } returns name
        }

        val uiState: StateFlow<StoreScreenData> = MutableStateFlow(StoreScreenData.Data(store))

        every { viewModel.uiState } returns uiState

        setupCompose()

        composeTestRule.onNodeWithText(name)
            .assertIsDisplayed()


        verify(exactly = 1) { viewModel.deals }
        verify(exactly = 1) { viewModel.dealDetails }
        verify(exactly = 1) { viewModel.uiState }
    }

    @Test
    fun onBackActioned() {
        val onBack: () -> Unit = mockk()

        every { onBack.invoke() } just runs

        setupCompose(onBack = onBack)

        composeTestRule.onNodeWithContentDescription(screenSemantics.back)
            .performClick()

        verify(exactly = 1) { viewModel.deals }
        verify(exactly = 1) { viewModel.dealDetails }
        verify(exactly = 1) { viewModel.uiState }
        verify(exactly = 1) { onBack.invoke() }
    }

    private fun hasRole(role: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private data class ScreenSemantics(
        val back: String,
    ) {
        companion object {
            @Composable
            fun load(): ScreenSemantics = ScreenSemantics(
                back = stringResource(Res.string.store_screen_navigation_back_icon),
            )

            @Composable
            fun dealRowCd(title: String, price: String): String =
                stringResource(Res.string.store_screen_deal_row_description, title, price)
        }
    }
}
