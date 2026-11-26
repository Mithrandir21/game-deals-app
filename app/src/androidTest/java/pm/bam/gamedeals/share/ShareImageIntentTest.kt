package pm.bam.gamedeals.share

import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.bam.gamedeals.common.ui.platform.AndroidPlatformActions
import java.io.File

/** A shared image reaches the share sheet with its text, readable by whichever app is picked (matrix 1.9). */
@RunWith(AndroidJUnit4::class)
class ShareImageIntentTest {

    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val started = mutableListOf<Intent>()
    private val context = object : ContextWrapper(app) {
        override fun startActivity(intent: Intent) {
            started += intent
        }
    }
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
    private val text = "Hades is 64% off https://gamedeals.bam.pm/g/018d937f-33f0-7200-80fc-87f769196c84"

    @Test
    fun the_share_sheet_gets_the_image_and_the_text_with_a_read_grant() = runBlocking {
        AndroidPlatformActions(context).shareImage(png, text)

        val chooser = started.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("image/png", send.type)
        assertEquals(text, send.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        app.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(png, it.readBytes()) }
    }

    @Test
    fun only_the_newest_image_is_kept() = runBlocking {
        val actions = AndroidPlatformActions(context)
        actions.shareImage(png, text)
        actions.shareImage(png, text)

        assertEquals(1, File(app.cacheDir, "shared").listFiles()!!.size)
    }
}
