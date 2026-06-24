package pm.bam.gamedeals.feature.bundles.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/** Client-side sort orders for the bundles list (the raw region feed is fetched once and re-sorted). */
enum class BundleSort { Newest, ExpiringSoon, Price }

/**
 * Loads the active bundles list (epic #205, Phase 3c; redesigned in the Bundles redesign). The region's
 * bundles are fetched once (cached read-through), then re-derived client-side as the [BundleSort] or the
 * persisted mature opt-in changes — no refetch. Mature bundles are hidden unless the user has opted in
 * (the same [SettingsRepository] flag the Deals tab uses).
 */
internal class BundlesViewModel(
    private val logger: Logger,
    private val bundlesRepository: BundlesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // null = not yet loaded (Loading); a value (possibly empty) = loaded.
    private val rawBundles = MutableStateFlow<List<Bundle>?>(null)
    private val loadError = MutableStateFlow(false)
    private val sort = MutableStateFlow(BundleSort.Newest)

    val uiState: StateFlow<BundlesScreenData> = combine(
        rawBundles,
        loadError,
        sort,
        settingsRepository.observeMatureOptIn(),
    ) { raw, error, sortOrder, matureOptIn ->
        when {
            error -> BundlesScreenData.Error
            raw == null -> BundlesScreenData.Loading
            else -> BundlesScreenData.Data(
                // Mature bundles stay hidden unless the app-wide opt-in (set in Account ▸ App) is on.
                bundles = raw.filter { matureOptIn || !it.isMature }.sorted(sortOrder).toImmutableList(),
                sort = sortOrder,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BundlesScreenData.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loadError.value = false
            rawBundles.value = null
            try {
                rawBundles.value = bundlesRepository.getBundles()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fatal(logger, t)
                loadError.value = true
            }
        }
    }

    fun setSort(newSort: BundleSort) = sort.update { newSort }

    /** Sentinels keep null publish/expiry/price at the end of each order without comparator nullability. */
    private fun List<Bundle>.sorted(order: BundleSort): List<Bundle> = when (order) {
        BundleSort.Newest -> sortedWith(
            compareByDescending<Bundle> { it.publishEpochMs ?: Long.MIN_VALUE }.thenByDescending { it.id },
        )
        BundleSort.ExpiringSoon -> sortedBy { it.expiryEpochMs ?: Long.MAX_VALUE }
        BundleSort.Price -> sortedBy { it.priceValue ?: Double.MAX_VALUE }
    }

    sealed class BundlesScreenData {
        data object Loading : BundlesScreenData()
        data object Error : BundlesScreenData()

        @Immutable
        data class Data(
            val bundles: ImmutableList<Bundle>,
            val sort: BundleSort = BundleSort.Newest,
        ) : BundlesScreenData()
    }
}
