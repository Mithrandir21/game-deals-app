package pm.bam.gamedeals.domain.models

/** IGDB image size transform tokens — see https://api-docs.igdb.com/#images. */
enum class IgdbImageSize(val token: String) {
    Thumb("t_thumb"),
    CoverBig("t_cover_big"),
    ScreenshotMed("t_screenshot_med"),
    ScreenshotBig("t_screenshot_big"),
    ScreenshotHuge("t_screenshot_huge"),
}

fun igdbImageUrl(imageId: String, size: IgdbImageSize): String =
    "https://images.igdb.com/igdb/image/upload/${size.token}/$imageId.jpg"
