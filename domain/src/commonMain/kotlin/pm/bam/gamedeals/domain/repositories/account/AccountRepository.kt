package pm.bam.gamedeals.domain.repositories.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.domain.source.ItadLoginSource
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/**
 * The user's ITAD account session (epic #219, Phase 2). [observeAuthState] reflects the persisted
 * token ([AuthTokenStore]); [login] runs the OAuth flow via [ItadLoginSource]; [logout] clears it.
 */
interface AccountRepository {
    fun observeAuthState(): Flow<AuthState>

    /** Runs the OAuth login; returns the signed-in user, or null if cancelled. */
    suspend fun login(): ItadUser?

    /**
     * Re-fetches the ITAD profile when the stored session has no username, and fills it in.
     *
     * Login deliberately survives a failed `/user/info` call rather than throwing away a perfectly good
     * token, which leaves the session authenticated but nameless. Without this the user stays nameless
     * until they sign out and back in; run at app scope on login, it self-heals on the next launch.
     * Best-effort and idempotent — a session that already has a name makes no network call at all.
     */
    suspend fun refreshUsernameIfMissing()

    suspend fun logout()
}

internal class AccountRepositoryImpl(
    private val authTokenStore: AuthTokenStore,
    private val loginSource: ItadLoginSource,
    private val accountSource: ItadAccountSource,
    private val analytics: Analytics,
) : AccountRepository {

    override fun observeAuthState(): Flow<AuthState> = authTokenStore.observeAuthState()

    override suspend fun login(): ItadUser? {
        val user = try {
            loginSource.login()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            analytics.capture(AnalyticsEvents.ACCOUNT_LOGIN, mapOf("result" to "error"))
            throw t
        }
        analytics.capture(AnalyticsEvents.ACCOUNT_LOGIN, mapOf("result" to if (user != null) "success" else "cancelled"))
        return user
    }

    override suspend fun refreshUsernameIfMissing() {
        if (!authTokenStore.getUsername().isNullOrBlank()) return
        if (authTokenStore.getAccessToken().isNullOrBlank()) return
        authTokenStore.updateUsername(accountSource.getUserInfo().username)
    }

    override suspend fun logout() {
        authTokenStore.clear()
        analytics.capture(AnalyticsEvents.ACCOUNT_LOGOUT)
    }
}
