package pm.bam.gamedeals.feature.giveaways.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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
    val uiState: StateFlow<GiveawaysScreenData> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GiveawaysScreenData()
    )


    init {
        viewModelScope.launch {
            flow { emitAll(giveawaysRepository.observeGiveaways()) }
                .map { GiveawaysScreenData(status = GiveawaysScreenStatus.SUCCESS, giveaways = it) }
                .logFlow(logger)
                .catch { emit(_uiState.value.copy(status = GiveawaysScreenStatus.ERROR)) }
                .collect { _uiState.emit(it) }
        }
    }

    fun reloadGiveaways() {
        viewModelScope.launch {
            flow { emit(_uiState.value.copy(status = GiveawaysScreenStatus.LOADING)) }
                .onStart { giveawaysRepository.refreshGiveaways() }
                .logFlow(logger)
                .catch { emit(_uiState.value.copy(status = GiveawaysScreenStatus.ERROR)) }
                .collect { _uiState.emit(it) }
        }
    }

    fun loadGiveaway(parameters: GiveawaySearchParameters) {
        viewModelScope.launch {
            flow { emitAll(giveawaysRepository.observeGiveaways(parameters)) }
                .map { GiveawaysScreenData(status = GiveawaysScreenStatus.SUCCESS, giveaways = it) }
                .logFlow(logger)
                .catch { emit(_uiState.value.copy(status = GiveawaysScreenStatus.ERROR)) }
                .collect { _uiState.emit(it) }
        }
    }


    data class GiveawaysScreenData(
        val status: GiveawaysScreenStatus = GiveawaysScreenStatus.LOADING,
        val giveaways: List<Giveaway> = emptyList()
    )


    internal enum class GiveawaysScreenStatus {
        LOADING, ERROR, SUCCESS
    }
}