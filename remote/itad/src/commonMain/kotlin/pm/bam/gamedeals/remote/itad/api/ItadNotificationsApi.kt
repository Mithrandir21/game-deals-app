package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadNotification
import pm.bam.gamedeals.remote.itad.models.RemoteItadNotificationWaitlist

/**
 * ITAD notifications endpoints (epic #272, P2.1 #277). OAuth-scoped (`notifications`) — the [httpClient]
 * must be the bearer client (`ITAD_AUTH_QUALIFIER`).
 *
 * `GET /notifications/v1` returns an array of notifications (a notification is unread while its `read`
 * field is null); the mark-read `PUT`s return `204 No Content` (verified against the ITAD OpenAPI).
 * `GET /notifications/waitlist/v1?id=` returns the per-notification waitlist detail (the referenced games)
 * for deep-linking (#288).
 */
class ItadNotificationsApi(private val httpClient: HttpClient) {

    suspend fun getNotifications(): ApiResponse<List<RemoteItadNotification>> = try {
        ApiResponse.Success(httpClient.get("/notifications/v1").body<List<RemoteItadNotification>>())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun getWaitlistDetail(id: String): ApiResponse<RemoteItadNotificationWaitlist> = try {
        ApiResponse.Success(
            httpClient.get("/notifications/waitlist/v1") { parameter("id", id) }.body<RemoteItadNotificationWaitlist>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun markRead(id: String): ApiResponse<Unit> = try {
        httpClient.put("/notifications/read/v1") { parameter("id", id) }
        ApiResponse.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun markAllRead(): ApiResponse<Unit> = try {
        httpClient.put("/notifications/read/all/v1")
        ApiResponse.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
