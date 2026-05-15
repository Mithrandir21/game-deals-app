package pm.bam.gamedeals.feature.favourites.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.domain.models.FavouriteGame
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepository
import pm.bam.gamedeals.logging.Logger

internal class FavouritesViewModel(
    private val logger: Logger,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {

    // Retry counter: every increment re-subscribes to `observeFavourites()`, restarting the
    // upstream Room flow. `flatMapLatest` cancels the in-flight inner flow before relaunching it.
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FavouritesScreenData> = retryTrigger
        .flatMapLatest {
            favouritesRepository.observeFavourites()
                .map { items ->
                    FavouritesScreenData(
                        status = FavouritesScreenStatus.SUCCESS,
                        favourites = items.toImmutableList(),
                    )
                }
                .onStart { emit(FavouritesScreenData()) }
                .catch { emit(FavouritesScreenData(status = FavouritesScreenStatus.ERROR)) }
        }
        .logFlow(logger)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FavouritesScreenData())

    fun retry() {
        retryTrigger.update { it + 1 }
    }


    @Immutable
    data class FavouritesScreenData(
        val status: FavouritesScreenStatus = FavouritesScreenStatus.LOADING,
        val favourites: ImmutableList<FavouriteGame> = persistentListOf(),
    )


    internal enum class FavouritesScreenStatus {
        LOADING, ERROR, SUCCESS
    }
}
