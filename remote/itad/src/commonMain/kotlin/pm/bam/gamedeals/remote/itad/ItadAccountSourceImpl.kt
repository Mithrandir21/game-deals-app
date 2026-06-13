package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.IgnoredEntry
import pm.bam.gamedeals.domain.models.ItadNote
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadCollectionApi
import pm.bam.gamedeals.remote.itad.api.ItadIgnoredApi
import pm.bam.gamedeals.remote.itad.api.ItadNotesApi
import pm.bam.gamedeals.remote.itad.api.ItadNotificationsApi
import pm.bam.gamedeals.remote.itad.api.ItadUserApi
import pm.bam.gamedeals.remote.itad.api.ItadWaitlistApi
import pm.bam.gamedeals.remote.itad.mappers.toCollectionEntry
import pm.bam.gamedeals.remote.itad.mappers.toIgnoredEntry
import pm.bam.gamedeals.remote.itad.mappers.toItadNote
import pm.bam.gamedeals.remote.itad.mappers.toItadNotification
import pm.bam.gamedeals.remote.itad.mappers.toNotificationGame
import pm.bam.gamedeals.remote.itad.mappers.toItadUser
import pm.bam.gamedeals.remote.itad.mappers.toWaitlistEntry
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

/**
 * ITAD implementation of the user-scoped [ItadAccountSource] port (epic #219, Phase 2). Every call goes
 * through the bearer (OAuth) client, so all operations require a valid user token; the bearer plugin
 * attaches and refreshes it transparently. Mirrors `ItadSourceImpl`'s log → transform → getOrThrow
 * pipeline.
 *
 * The add/remove operations take a single game id and send it as a one-element id array (the ITAD
 * waitlist/collection PUT/DELETE bodies are arrays of game-id strings).
 */
internal class ItadAccountSourceImpl(
    private val logger: Logger,
    private val userApi: ItadUserApi,
    private val waitlistApi: ItadWaitlistApi,
    private val collectionApi: ItadCollectionApi,
    private val notificationsApi: ItadNotificationsApi,
    private val ignoredApi: ItadIgnoredApi,
    private val notesApi: ItadNotesApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
) : ItadAccountSource {

    override suspend fun getUserInfo(): ItadUser =
        userApi.getInfo()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toItadUser()

    override suspend fun getWaitlist(): List<WaitlistEntry> =
        waitlistApi.getWaitlist()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toWaitlistEntry() }

    override suspend fun addToWaitlist(gameId: String) {
        waitlistApi.addGames(listOf(gameId))
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    override suspend fun removeFromWaitlist(gameId: String) {
        waitlistApi.removeGames(listOf(gameId))
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    override suspend fun getCollection(): List<CollectionEntry> =
        collectionApi.getCollection()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toCollectionEntry() }

    override suspend fun addToCollection(gameId: String) {
        collectionApi.addGames(listOf(gameId))
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    override suspend fun removeFromCollection(gameId: String) {
        collectionApi.removeGames(listOf(gameId))
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    override suspend fun getNotifications(): List<ItadNotification> =
        notificationsApi.getNotifications()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toItadNotification() }

    override suspend fun markNotificationRead(id: String) {
        notificationsApi.markRead(id)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    override suspend fun markAllNotificationsRead() {
        notificationsApi.markAllRead()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    override suspend fun getWaitlistNotificationGames(id: String): List<NotificationGame> =
        notificationsApi.getWaitlistDetail(id)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .games
            .map { it.toNotificationGame() }

    override suspend fun getIgnored(): List<IgnoredEntry> =
        ignoredApi.getIgnored()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toIgnoredEntry() }

    override suspend fun addToIgnored(gameId: String) {
        ignoredApi.addGames(listOf(gameId))
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    override suspend fun removeFromIgnored(gameId: String) {
        ignoredApi.removeGames(listOf(gameId))
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    override suspend fun getNotes(): List<ItadNote> =
        notesApi.getNotes()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toItadNote() }

    override suspend fun setNote(gameId: String, note: String) {
        notesApi.putNote(gameId, note)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    override suspend fun removeNote(gameId: String) {
        notesApi.deleteNote(gameId)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
    }

    private companion object {
        private const val TAG = "ItadAccountSource"
    }
}
