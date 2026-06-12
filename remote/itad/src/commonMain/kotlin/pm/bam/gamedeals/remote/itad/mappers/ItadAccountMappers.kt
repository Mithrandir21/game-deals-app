package pm.bam.gamedeals.remote.itad.mappers

import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.IgnoredEntry
import pm.bam.gamedeals.domain.models.ItadNote
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.remote.itad.models.RemoteItadNote
import pm.bam.gamedeals.remote.itad.models.RemoteItadNotification
import pm.bam.gamedeals.remote.itad.models.RemoteItadSearchGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadUser
import pm.bam.gamedeals.remote.itad.models.bestArt

internal fun RemoteItadUser.toItadUser(): ItadUser = ItadUser(username = username)

internal fun RemoteItadNotification.toItadNotification(): ItadNotification =
    ItadNotification(id = id, type = type, title = title, timestamp = timestamp, read = read != null)

internal fun RemoteItadNote.toItadNote(): ItadNote = ItadNote(gameId = gid, note = note)

internal fun RemoteItadSearchGame.toWaitlistEntry(): WaitlistEntry =
    WaitlistEntry(gameId = id, title = title, boxart = boxartUrl())

internal fun RemoteItadSearchGame.toCollectionEntry(): CollectionEntry =
    CollectionEntry(gameId = id, title = title, boxart = boxartUrl())

internal fun RemoteItadSearchGame.toIgnoredEntry(): IgnoredEntry =
    IgnoredEntry(gameId = id, title = title, boxart = boxartUrl())

private fun RemoteItadSearchGame.boxartUrl(): String? = assets.bestArt()
