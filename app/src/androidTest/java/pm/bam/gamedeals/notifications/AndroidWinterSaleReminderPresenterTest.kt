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

/** The Winter Sale reminder as the OS sees it: one notification on the sale channel that opens the guide. */
@RunWith(AndroidJUnit4::class)
class AndroidWinterSaleReminderPresenterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        manager.cancel(AndroidWinterSaleReminderPresenter.NOTIFICATION_ID)
    }

    @After
    fun tearDown() = manager.cancel(AndroidWinterSaleReminderPresenter.NOTIFICATION_ID)

    @Test
    fun posts_one_auto_cancelling_reminder_on_the_sale_channel() = runBlocking {
        val presenter = AndroidWinterSaleReminderPresenter(context)

        presenter.present()
        presenter.present() // a second fire replaces the first rather than stacking

        val posted = waitForReminder()
        assertEquals(1, manager.activeNotifications.count { it.id == AndroidWinterSaleReminderPresenter.NOTIFICATION_ID })
        assertEquals(AndroidWinterSaleReminderPresenter.CHANNEL_ID, posted.channelId)
        assertEquals(context.getString(R.string.notification_sale_reminder_title), posted.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertNotNull(posted.contentIntent)
        assertEquals(
            context.getString(R.string.notification_channel_sale_reminder),
            manager.getNotificationChannel(AndroidWinterSaleReminderPresenter.CHANNEL_ID).name,
        )
    }

    @Test
    fun the_tap_intent_opens_the_app_on_the_guide() {
        val intent = winterSaleGuideIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(NotificationRoute.WinterSaleGuide, intent.toNotificationRoute())
    }

    private fun waitForReminder(): Notification {
        repeat(50) {
            manager.activeNotifications.firstOrNull { it.id == AndroidWinterSaleReminderPresenter.NOTIFICATION_ID }?.let { return it.notification }
            Thread.sleep(100)
        }
        error("The Winter Sale reminder was not posted")
    }
}
