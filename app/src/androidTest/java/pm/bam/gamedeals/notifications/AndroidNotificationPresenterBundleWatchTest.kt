package pm.bam.gamedeals.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pm.bam.gamedeals.MainActivity
import pm.bam.gamedeals.R
import pm.bam.gamedeals.common.navigation.NotificationRoute
import pm.bam.gamedeals.domain.repositories.notifications.AlertKind
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert
import pm.bam.gamedeals.logging.analytics.NoOpAnalytics

/** Bundle watch alerts as the OS sees them: one summary per kind on the waitlist channel, opening Bundles or Giveaways (matrix 6.6). */
@RunWith(AndroidJUnit4::class)
class AndroidNotificationPresenterBundleWatchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val ids = listOf(BUNDLE_WATCH_BUNDLES_ID, BUNDLE_WATCH_GIVEAWAYS_ID)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ids.forEach(manager::cancel)
    }

    @After
    fun tearDown() = ids.forEach(manager::cancel)

    private fun alert(id: String, title: String, kind: AlertKind) =
        PendingNotificationAlert(notificationId = id, title = title, games = emptyList(), gameId = id, kind = kind)

    @Test
    fun bundles_and_giveaways_each_post_one_summary_on_the_waitlist_channel() = runBlocking {
        AndroidNotificationPresenter(context, NoOpAnalytics).present(
            listOf(
                alert("b1", "Hades is in Humble Choice, from \$11.99", AlertKind.WAITLIST_BUNDLE),
                alert("b2", "Celeste is in Indie Bundle, from \$1", AlertKind.WAITLIST_BUNDLE),
                alert("g1", "Hades is free on Epic Games", AlertKind.WAITLIST_GIVEAWAY),
            ),
        )

        val bundles = waitFor(BUNDLE_WATCH_BUNDLES_ID)
        assertEquals(AndroidNotificationPresenter.CHANNEL_ID, bundles.channelId)
        assertEquals(context.getString(R.string.notification_bundle_watch_bundles_title), bundles.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Hades is in Humble Choice, from \$11.99", bundles.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(bundles.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertNotNull(bundles.contentIntent)

        val giveaways = waitFor(BUNDLE_WATCH_GIVEAWAYS_ID)
        assertEquals(context.getString(R.string.notification_bundle_watch_giveaways_title), giveaways.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Hades is free on Epic Games", giveaways.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun the_taps_open_the_app_on_bundles_and_on_giveaways() {
        val bundles = notificationRouteIntent(context, ROUTE_BUNDLES)
        val giveaways = notificationRouteIntent(context, ROUTE_GIVEAWAYS)

        assertEquals(MainActivity::class.java.name, bundles.component?.className)
        assertEquals(NotificationRoute.Bundles, bundles.toNotificationRoute())
        assertEquals(NotificationRoute.Giveaways, giveaways.toNotificationRoute())
    }

    private fun waitFor(id: Int): Notification {
        repeat(50) {
            manager.activeNotifications.firstOrNull { it.id == id }?.let { return it.notification }
            Thread.sleep(100)
        }
        error("Notification $id was not posted")
    }
}
