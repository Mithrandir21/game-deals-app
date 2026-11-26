package pm.bam.gamedeals.testing.fixtures

import pm.bam.gamedeals.domain.models.Store

fun store(
    storeID: Int = 1,
    storeName: String = "Test Store",
    isActive: Boolean = true,
    images: Store.StoreImages = Store.StoreImages(banner = "banner", logo = "logo", icon = "icon"),
    iconUrl: String = "logo",
    expires: Long = 0L,
) = Store(
    storeID = storeID,
    storeName = storeName,
    isActive = isActive,
    images = images,
    iconUrl = iconUrl,
    expires = expires,
)
