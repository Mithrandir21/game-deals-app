package pm.bam.gamedeals.remote.cheapshark.mappers

import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.remote.cheapshark.models.RemoteStore

private const val IMAGE_BASE = "https://www.cheapshark.com"

internal fun RemoteStore.RemoteStoreImages.toStoreImages(): Store.StoreImages =
    Store.StoreImages(
        banner = IMAGE_BASE.plus(banner),
        logo = IMAGE_BASE.plus(logo),
        icon = IMAGE_BASE.plus(icon)
    )

internal fun RemoteStore.toStore(): Store =
    Store(
        storeID = storeID,
        storeName = storeName,
        isActive = isActive.toBooleanStrict(),
        images = images.toStoreImages()
    )
