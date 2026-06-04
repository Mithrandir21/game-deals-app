package pm.bam.gamedeals.domain.repositories.account

import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState

/**
 * The user's ITAD account session (epic #219, Phase 2).
 *
 * Phase 0 wires only the auth state (from [AuthTokenStore], which is empty until login exists). The
 * OAuth login flow, profile, and stat-card data land in Phase 2 (#226 / #228), at which point this
 * repository gains an [pm.bam.gamedeals.domain.source.ItadAccountSource] dependency.
 */
interface AccountRepository {
    fun observeAuthState(): Flow<AuthState>
    suspend fun logout()
}

internal class AccountRepositoryImpl(
    private val authTokenStore: AuthTokenStore,
) : AccountRepository {

    override fun observeAuthState(): Flow<AuthState> = authTokenStore.observeAuthState()

    override suspend fun logout() = authTokenStore.clear()
}
