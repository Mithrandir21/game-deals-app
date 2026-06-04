package pm.bam.gamedeals.remote.itad.mappers

import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.remote.itad.models.RemoteItadSearchGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadUser

internal fun RemoteItadUser.toItadUser(): ItadUser = ItadUser(username = username)

internal fun RemoteItadSearchGame.toWaitlistEntry(): WaitlistEntry =
    WaitlistEntry(gameId = id, title = title, boxart = boxartUrl())

internal fun RemoteItadSearchGame.toCollectionEntry(): CollectionEntry =
    CollectionEntry(gameId = id, title = title, boxart = boxartUrl())

private fun RemoteItadSearchGame.boxartUrl(): String? =
    assets?.boxart ?: assets?.banner300 ?: assets?.banner600
