package pm.bam.gamedeals.domain.repositories.stores

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.verbose
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoresRepository @Inject internal constructor(
    private val logger: Logger,
    private val storesDao: StoresDao,
    private val cheapsharkSource: CheapsharkSource
) {

    fun observeStores(): Flow<List<Store>> =
        storesDao.observeAllStores()
            .onStart { refreshStores() }

    suspend fun getStore(storeId: Int): Store =
        storesDao.getStore(storeId)

    suspend fun refreshStores(force: Boolean = false) {
        val refresh = force || refreshNeeded()

        debug(logger) { "Stores refresh needed: $refresh" }

        if (refresh) {
            cheapsharkSource.fetchStores()
                .let { storesDao.addStores(*it.toTypedArray()) }
        }
    }

    private suspend fun refreshNeeded(): Boolean =
        storesDao.getAllStores()
            .let { stores -> stores.isEmpty() || stores.any { it.expires < System.currentTimeMillis() } }
            .apply { verbose(logger) { "Stores Expiration logic returned: $this" } }
}
