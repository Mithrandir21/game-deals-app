package pm.bam.gamedeals.testing.fixtures

import pm.bam.gamedeals.domain.models.Store

fun store(
    storeID: Int = 1,
    storeName: String = "Test Store",
    isActive: Boolean = true,
    images: Store.StoreImages = Store.StoreImages(banner = "banner", logo = "logo", icon = "icon"),
    expires: Long = 0L,
) = Store(storeID, storeName, isActive, images, expires)
