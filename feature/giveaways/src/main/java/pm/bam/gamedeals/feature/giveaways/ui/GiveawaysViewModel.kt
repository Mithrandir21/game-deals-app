package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.logging.Logger
import javax.inject.Inject


@HiltViewModel
internal class GiveawaysViewModel @Inject constructor(
    private val logger: Logger,
    private val giveawaysRepository: GiveawaysRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GiveawaysScreenData())
    val uiState: StateFlow<GiveawaysScreenData> = _uiState.asStateFlow()


    init {
        viewModelScope.launch {
            flow { emitAll(giveawaysRepository.observeGiveaways()) }
                .map { GiveawaysScreenData(status = GiveawaysScreenStatus.SUCCESS, giveaways = it.toImmutableList()) }
                .logFlow(logger)
                .catch { emit(_uiState.value.copy(status = GiveawaysScreenStatus.ERROR)) }
                .collect { _uiState.emit(it) }
        }
    }

    fun reloadGiveaways() {
        viewModelScope.launch {
            flow {
                emit(_uiState.value.copy(status = GiveawaysScreenStatus.LOADING))
                giveawaysRepository.refreshGiveaways()
            }
                .logFlow(logger)
                .catch { emit(_uiState.value.copy(status = GiveawaysScreenStatus.ERROR)) }
                .collect { _uiState.emit(it) }
        }
    }

    fun loadGiveaway(parameters: GiveawaySearchParameters) {
        viewModelScope.launch {
            flow { emitAll(giveawaysRepository.observeGiveaways(parameters)) }
                .map { GiveawaysScreenData(status = GiveawaysScreenStatus.SUCCESS, giveaways = it.toImmutableList()) }
                .logFlow(logger)
                .catch { emit(_uiState.value.copy(status = GiveawaysScreenStatus.ERROR)) }
                .collect { _uiState.emit(it) }
        }
    }


    @Immutable
    data class GiveawaysScreenData(
        val status: GiveawaysScreenStatus = GiveawaysScreenStatus.LOADING,
        val giveaways: ImmutableList<Giveaway> = persistentListOf()
    )


    internal enum class GiveawaysScreenStatus {
        LOADING, ERROR, SUCCESS
    }
}