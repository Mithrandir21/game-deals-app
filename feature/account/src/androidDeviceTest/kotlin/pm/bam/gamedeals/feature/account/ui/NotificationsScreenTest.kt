package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_unread_state
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_mark_all_read
import pm.bam.gamedeals.feature.account.generated.resources.account_notifications_more_actions
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationDay
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationGameThumb
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationsScreenData

/**
 * Device UI coverage for [NotificationsScreen], expanded from the original unread-stateDescription a11y
 * tests to also cover the empty state and the day-row, mark-all-read, and back interactions — driven
 * through a mocked [NotificationsViewModel].
 */
class NotificationsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: NotificationsViewModel = mockk(relaxed = true)
    private val onBack = mockk<() -> Unit>(relaxed = true)
    private val onOpenDay = mockk<(String) -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private fun setup(days: ImmutableList<NotificationDay>) {
        every { viewModel.uiState } returns MutableStateFlow(NotificationsScreenData(loading = false, days = days))
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                NotificationsScreen(onBack = onBack, onOpenDay = onOpenDay, viewModel = viewModel)
            }
        }
    }

    private fun day(date: String, hasUnread: Boolean, games: ImmutableList<NotificationGameThumb> = persistentListOf()) =
        NotificationDay(date = date, games = games, count = 3, hasUnread = hasUnread)

    @Test
    fun unreadRowExposesUnreadStateDescription() {
        setup(persistentListOf(day("2026-06-18", hasUnread = true), day("2026-06-17", hasUnread = false)))

        composeTestRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, labels.unread))
            .assertCountEquals(1)
    }

    @Test
    fun allReadListExposesNoUnreadStateDescription() {
        setup(persistentListOf(day("2026-06-18", hasUnread = false), day("2026-06-17", hasUnread = false)))

        composeTestRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, labels.unread))
            .assertCountEquals(0)
    }

    @Test
    fun emptyStateShowsMessage() {
        setup(persistentListOf())

        composeTestRule.onNodeWithText(labels.empty).assertIsDisplayed()
    }

    @Test
    fun tappingDayRowOpensThatDay() {
        setup(persistentListOf(day(DATE, hasUnread = false, games = persistentListOf(NotificationGameThumb("g1", GAME_TITLE, null)))))

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { onOpenDay(DATE) }
    }

    @Test
    fun markAllReadDispatchesToViewModel() {
        setup(persistentListOf(day(DATE, hasUnread = true, games = persistentListOf(NotificationGameThumb("g1", GAME_TITLE, null)))))

        composeTestRule.onNodeWithContentDescription(labels.moreActions).performClick()
        composeTestRule.onNodeWithText(labels.markAllRead).performClick()

        verify(exactly = 1) { viewModel.onMarkAllRead() }
    }

    @Test
    fun backIconDispatchesOnBack() {
        setup(persistentListOf(day(DATE, hasUnread = false)))

        composeTestRule.onNodeWithContentDescription(labels.back).performClick()

        verify(exactly = 1) { onBack() }
    }

    private data class Labels(
        val unread: String,
        val empty: String,
        val moreActions: String,
        val markAllRead: String,
        val back: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                unread = stringResource(Res.string.account_notification_unread_state),
                empty = stringResource(Res.string.account_notifications_empty),
                moreActions = stringResource(Res.string.account_notifications_more_actions),
                markAllRead = stringResource(Res.string.account_notifications_mark_all_read),
                back = stringResource(Res.string.account_navigation_back),
            )
        }
    }

    private companion object {
        const val DATE = "2026-06-18"
        const val GAME_TITLE = "Halo"
    }
}
