@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package pm.bam.gamedeals.common.ui.platform

import platform.Foundation.NSItemProvider
import platform.LinkPresentation.LPLinkMetadata
import platform.UIKit.UIActivityItemSourceProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.darwin.NSObject

/**
 * A shared card image for the share sheet. Its link metadata makes the sheet's header preview the card itself instead
 * of the share text. The header title is the text without its link: no gamedeals.bam.pm address on screen (Bam,
 * 2026-11-24). Every share target still gets the plain image.
 */
internal class ShareImageItemSource(
    private val image: UIImage,
    text: String,
) : NSObject(), UIActivityItemSourceProtocol {

    private val headerTitle = text.substringBefore("https://").trim().trimEnd(':', '—', '-').trim()

    override fun activityViewControllerPlaceholderItem(activityViewController: UIActivityViewController): Any = image

    override fun activityViewController(activityViewController: UIActivityViewController, itemForActivityType: String?): Any? = image

    // UIKit only forward-declares LPLinkMetadata, hence the cast to that declaration.
    @Suppress("CAST_NEVER_SUCCEEDS")
    override fun activityViewControllerLinkMetadata(activityViewController: UIActivityViewController): objcnames.classes.LPLinkMetadata? {
        val metadata = LPLinkMetadata()
        if (headerTitle.isNotEmpty()) metadata.setTitle(headerTitle)
        UIImagePNGRepresentation(image)?.let { png ->
            val preview = NSItemProvider(item = png, typeIdentifier = "public.png")
            metadata.setImageProvider(preview)
            metadata.setIconProvider(preview)
        }
        return metadata as objcnames.classes.LPLinkMetadata
    }
}
