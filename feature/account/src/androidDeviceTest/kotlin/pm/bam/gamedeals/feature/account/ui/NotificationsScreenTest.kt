package pm.bam.gamedeals.feature.account.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_unread_state
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationDay
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationsScreenData

/**
 * Locks the accessibility fix where an unread notification day's status was conveyed only by a colored
 * dot + bold weight (both visual-only). Each unread day row must expose an "Unread" stateDescription so
 * TalkBack can distinguish it from a read one; read rows must expose none.
 */
class NotificationsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: NotificationsViewModel = mockk(relaxed = true)

    private lateinit var unreadLabel: String

    private fun setup(days: ImmutableList<NotificationDay>) {
        every { viewModel.uiState } returns MutableStateFlow(
            NotificationsScreenData(loading = false, days = days),
        )
        composeTestRule.setContent {
            unreadLabel = stringResource(Res.string.account_notification_unread_state)
            GameDealsTheme {
                NotificationsScreen(onBack = {}, onOpenDay = {}, viewModel = viewModel)
            }
        }
    }

    private fun day(date: String, hasUnread: Boolean) =
        NotificationDay(date = date, games = persistentListOf(), count = 3, hasUnread = hasUnread)

    @Test
    fun unreadRowExposesUnreadStateDescription() {
        setup(persistentListOf(day("2026-06-18", hasUnread = true), day("2026-06-17", hasUnread = false)))

        composeTestRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, unreadLabel))
            .assertCountEquals(1)
    }

    @Test
    fun allReadListExposesNoUnreadStateDescription() {
        setup(persistentListOf(day("2026-06-18", hasUnread = false), day("2026-06-17", hasUnread = false)))

        composeTestRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, unreadLabel))
            .assertCountEquals(0)
    }
}
