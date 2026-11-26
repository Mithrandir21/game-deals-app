package pm.bam.gamedeals.remote.igdb.mappers

import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagDimension
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbTag

/**
 * Maps a vocabulary row to a domain [IgdbTag], tagging it with the [dimension] of the endpoint it
 * came from. Rows without a name are dropped — a nameless tag can't be shown in the picker.
 */
internal fun RemoteIgdbTag.toIgdbTagOrNull(dimension: IgdbTagDimension): IgdbTag? {
    val tagName = name ?: return null
    return IgdbTag(dimension = dimension, igdbId = id, name = tagName, slug = slug)
}
