package pm.bam.gamedeals.feature.store.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.store.ui.StoreViewModel.StoreScreenData

class StoreScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val deal: Deal = mockk()

    private val viewModel: StoreViewModel = mockk()

    private val storeId = 1

    @Before
    fun setup() {
        val dealId = "DealId"
        val dealTitle = "Title"
        val dealPrice = "Price"
        val dealThumb = "DealThumbnail"
        every { deal.dealID } returns dealId
        every { deal.storeID } returns storeId
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
    }


    @Test
    fun loadSingleDeal() {
        composeTestRule.setContent {
            GameDealsTheme {
                StoreScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(DealRowTag.plus(deal.dealID))
            .assertIsDisplayed()

        verify(exactly = 1) { viewModel.deals }
        verify(exactly = 1) { viewModel.dealDetails }
        verify(exactly = 1) { viewModel.uiState }
    }

    @Test
    fun loadDealDetails() {
        every { viewModel.loadDealDetails(any(), any(), any(), any(), any()) } just runs

        composeTestRule.setContent {
            GameDealsTheme {
                StoreScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(DealRowTag.plus(deal.dealID))
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

        composeTestRule.setContent {
            GameDealsTheme {
                StoreScreen(
                    onBack = {},
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(StoreTopBarTag)
            .onChildren()
            .filterToOne(hasText(name))
            .assertIsDisplayed()


        verify(exactly = 1) { viewModel.deals }
        verify(exactly = 1) { viewModel.dealDetails }
        verify(exactly = 1) { viewModel.uiState }
    }

    @Test
    fun onBackActioned() {
        val onBack: () -> Unit = mockk()

        every { onBack.invoke() } just runs

        composeTestRule.setContent {
            GameDealsTheme {
                StoreScreen(
                    onBack = onBack,
                    goToWeb = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithTag(TopBarNavTag)
            .performClick()

        verify(exactly = 1) { viewModel.deals }
        verify(exactly = 1) { viewModel.dealDetails }
        verify(exactly = 1) { viewModel.uiState }
        verify(exactly = 1) { onBack.invoke() }
    }
}
