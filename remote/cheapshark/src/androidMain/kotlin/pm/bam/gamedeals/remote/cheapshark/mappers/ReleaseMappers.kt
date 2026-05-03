package pm.bam.gamedeals.remote.cheapshark.mappers

import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.remote.cheapshark.models.RemoteRelease

internal fun RemoteRelease.toRelease(): Release =
    Release(
        title = title,
        date = date,
        image = image,
    )
