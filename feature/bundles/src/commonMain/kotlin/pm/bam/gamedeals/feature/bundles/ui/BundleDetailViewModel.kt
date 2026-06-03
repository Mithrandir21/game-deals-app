package pm.bam.gamedeals.feature.bundles.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Resolves a single [Bundle] by id for the detail screen (epic #205, Phase 3c). Bundles are not cached,
 * so the repository re-fetches the list and finds the id; a missing id surfaces as [Error].
 */
internal class BundleDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val bundlesRepository: BundlesRepository,
) : ViewModel() {

    private val bundleId: Int? = savedStateHandle.get<Int>("bundleId")

    val uiState: StateFlow<BundleDetailScreenData>
        field = MutableStateFlow<BundleDetailScreenData>(BundleDetailScreenData.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState.value = BundleDetailScreenData.Loading
            val id = bundleId
            if (id == null) {
                uiState.value = BundleDetailScreenData.Error
                return@launch
            }
            try {
                val bundle = bundlesRepository.getBundle(id)
                uiState.value = bundle?.let { BundleDetailScreenData.Data(it) } ?: BundleDetailScreenData.Error
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fatal(logger, t)
                uiState.value = BundleDetailScreenData.Error
            }
        }
    }

    sealed class BundleDetailScreenData {
        data object Loading : BundleDetailScreenData()
        data object Error : BundleDetailScreenData()

        @Immutable
        data class Data(val bundle: Bundle) : BundleDetailScreenData()
    }
}
