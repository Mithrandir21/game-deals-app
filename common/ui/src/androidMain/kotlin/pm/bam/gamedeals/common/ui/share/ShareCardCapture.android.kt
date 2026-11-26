package pm.bam.gamedeals.common.ui.share

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

internal actual fun ImageBitmap.encodePng(): ByteArray {
    val bitmap = asAndroidBitmap().let {
        // A captured layer can come back as a GPU-only HARDWARE bitmap; copy it into memory before encoding.
        if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
    } ?: return ByteArray(0)
    return ByteArrayOutputStream().use { out ->
        if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else ByteArray(0)
    }
}
