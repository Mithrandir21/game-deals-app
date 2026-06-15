package pm.bam.gamedeals.domain.repositories.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.domain.models.DealsFilter

/**
 * Cross-app user preferences persisted via [Storage] (the same SharedPreferences/NSUserDefaults-backed
 * store [pm.bam.gamedeals.domain.repositories.region.RegionRepository] uses). Currently holds the mature
 * opt-in, which gates adult content on both the Deals tab (`/deals/v2`) and the Bundles tab — so the
 * choice is remembered across launches and shared between the two screens.
 *
 * Defaults to off (adult content excluded) when nothing is stored. The value is exposed reactively so the
 * lists re-filter/re-fetch the moment the toggle flips.
 */
interface SettingsRepository {
    /** Emits the current mature opt-in, seeded from storage on first collection. */
    fun observeMatureOptIn(): Flow<Boolean>
    suspend fun getMatureOptIn(): Boolean
    suspend fun setMatureOptIn(enabled: Boolean)

    /** Emits the current Deals-tab filter, seeded from storage on first collection (empty by default). */
    fun observeDealsFilter(): Flow<DealsFilter>
    suspend fun getDealsFilter(): DealsFilter
    suspend fun setDealsFilter(filter: DealsFilter)
}

internal const val MATURE_OPT_IN_KEY = "mature_opt_in"
internal const val DEALS_FILTER_KEY = "deals_filter"

internal class SettingsRepositoryImpl(
    private val storage: Storage,
) : SettingsRepository {

    // Reactive source of truth, lazily seeded from [storage] on first access (null = not yet loaded).
    private val matureOptIn = MutableStateFlow<Boolean?>(null)

    // Deals filter source of truth, lazily seeded from [storage] (null = not yet loaded; absent = empty).
    private val dealsFilter = MutableStateFlow<DealsFilter?>(null)

    override fun observeMatureOptIn(): Flow<Boolean> =
        matureOptIn
            .onStart { if (matureOptIn.value == null) matureOptIn.value = loadMatureFromStorage() }
            .filterNotNull()

    override suspend fun getMatureOptIn(): Boolean {
        if (matureOptIn.value == null) matureOptIn.value = loadMatureFromStorage()
        return matureOptIn.value ?: false
    }

    override suspend fun setMatureOptIn(enabled: Boolean) {
        storage.save(MATURE_OPT_IN_KEY, enabled)
        matureOptIn.value = enabled
    }

    override fun observeDealsFilter(): Flow<DealsFilter> =
        dealsFilter
            .onStart { if (dealsFilter.value == null) dealsFilter.value = loadDealsFilterFromStorage() }
            .filterNotNull()

    override suspend fun getDealsFilter(): DealsFilter {
        if (dealsFilter.value == null) dealsFilter.value = loadDealsFilterFromStorage()
        return dealsFilter.value ?: DealsFilter()
    }

    override suspend fun setDealsFilter(filter: DealsFilter) {
        storage.save(DEALS_FILTER_KEY, filter)
        dealsFilter.value = filter
    }

    private suspend fun loadMatureFromStorage(): Boolean =
        runCatching { storage.getNullable<Boolean>(MATURE_OPT_IN_KEY) }.getOrNull() ?: false

    private suspend fun loadDealsFilterFromStorage(): DealsFilter =
        runCatching { storage.getNullable<DealsFilter>(DEALS_FILTER_KEY) }.getOrNull() ?: DealsFilter()
}
