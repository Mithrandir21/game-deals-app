package pm.bam.gamedeals.remote.signals

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pm.bam.gamedeals.domain.models.GameSignals
import pm.bam.gamedeals.domain.models.GameSignalsBatch
import pm.bam.gamedeals.domain.source.GameSignalsSource

/**
 * `POST https://gamedeals.bam.pm/api/signals/v1` (`store/site/signals-routes.mjs`). The ids go in the body, not the
 * URL; the Worker uses them for this one answer and keeps nothing about the phone that asked.
 */
internal class GameSignalsHttpSource(private val client: HttpClient) : GameSignalsSource {

    override suspend fun fetch(gameIds: List<String>): GameSignalsBatch {
        require(gameIds.size in 1..GameSignalsSource.MAX_IDS) { "1 to ${GameSignalsSource.MAX_IDS} ids per call" }
        val response: RemoteSignalsResponse = client.post(PATH) {
            contentType(ContentType.Application.Json)
            setBody(RemoteSignalsRequest(gameIds))
        }.body()
        return GameSignalsBatch(
            games = response.games.map { (id, signals) -> signals.toDomain(id) },
            pending = response.pending,
        )
    }

    private companion object {
        const val PATH = "/api/signals/v1"
    }
}

@Serializable
internal data class RemoteSignalsRequest(@SerialName("ids") val ids: List<String>)

@Serializable
internal data class RemoteSignalsResponse(
    @SerialName("games") val games: Map<String, RemoteGameSignals> = emptyMap(),
    @SerialName("pending") val pending: List<String> = emptyList(),
)

@Serializable
internal data class RemoteGameSignals(
    @SerialName("updated") val updated: Long,
    @SerialName("weeklyMaxCut") val weeklyMaxCut: List<Int> = emptyList(),
    @SerialName("review") val review: RemoteSignalsReview? = null,
    @SerialName("players") val players: RemoteSignalsPlayers? = null,
    @SerialName("waitlisted") val waitlisted: Int? = null,
    @SerialName("collected") val collected: Int? = null,
    @SerialName("rank") val rank: Int? = null,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("developer") val developer: String? = null,
    @SerialName("releaseYear") val releaseYear: Int? = null,
    @SerialName("steamAppId") val steamAppId: Int? = null,
) {
    fun toDomain(gameId: String) = GameSignals(
        gameId = gameId,
        updatedEpochMs = updated,
        weeklyMaxCut = weeklyMaxCut.map { it.coerceIn(0, 100) },
        reviewScore = review?.score,
        reviewCount = review?.count,
        reviewSource = review?.source,
        playersRecent = players?.recent,
        playersPeak = players?.peak,
        waitlisted = waitlisted,
        collected = collected,
        rank = rank,
        tags = tags,
        developer = developer,
        releaseYear = releaseYear,
        steamAppId = steamAppId,
    )
}

@Serializable
internal data class RemoteSignalsReview(
    @SerialName("score") val score: Int? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("source") val source: String? = null,
)

@Serializable
internal data class RemoteSignalsPlayers(
    @SerialName("recent") val recent: Int? = null,
    @SerialName("day") val day: Int? = null,
    @SerialName("week") val week: Int? = null,
    @SerialName("peak") val peak: Int? = null,
)
