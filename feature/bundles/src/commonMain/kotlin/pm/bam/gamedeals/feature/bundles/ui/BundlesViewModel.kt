package pm.bam.gamedeals.feature.bundles.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Loads the active bundles list (epic #205, Phase 3c). Bundles are fetched fresh (not cached), so this
 * is a one-shot load with explicit loading/error/success states and a [load] retry.
 */
internal class BundlesViewModel(
    private val logger: Logger,
    private val bundlesRepository: BundlesRepository,
) : ViewModel() {

    val uiState: StateFlow<BundlesScreenData>
        field = MutableStateFlow<BundlesScreenData>(BundlesScreenData.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState.value = BundlesScreenData.Loading
            try {
                uiState.value = BundlesScreenData.Data(bundlesRepository.getBundles().toImmutableList())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fatal(logger, t)
                uiState.value = BundlesScreenData.Error
            }
        }
    }

    sealed class BundlesScreenData {
        data object Loading : BundlesScreenData()
        data object Error : BundlesScreenData()

        @Immutable
        data class Data(val bundles: ImmutableList<Bundle>) : BundlesScreenData()
    }
}
