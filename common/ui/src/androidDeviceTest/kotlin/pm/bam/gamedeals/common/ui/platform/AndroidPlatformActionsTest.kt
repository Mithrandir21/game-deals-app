package pm.bam.gamedeals.common.ui.platform

import android.content.ContextWrapper
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the image can't be handed over, the text still goes out (matrix 1.10). */
class AndroidPlatformActionsTest {

    private val started = mutableListOf<Intent>()
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun startActivity(intent: Intent) {
            started += intent
        }
    }

    @Test
    fun without_a_place_to_put_the_image_the_text_is_shared_on_its_own() = runBlocking {
        // This test package declares no FileProvider, like an app that forgot to: the image can't be written.
        AndroidPlatformActions(context).shareImage(byteArrayOf(1, 2, 3), "Hades https://gamedeals.bam.pm/g/x")

        val send = IntentCompat.getParcelableExtra(started.single(), Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("text/plain", send.type)
        assertEquals("Hades https://gamedeals.bam.pm/g/x", send.getStringExtra(Intent.EXTRA_TEXT))
    }
}
