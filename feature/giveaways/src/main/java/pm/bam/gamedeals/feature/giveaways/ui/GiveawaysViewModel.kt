package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.logging.Logger
import javax.inject.Inject


@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class GiveawaysViewModel @Inject constructor(
    private val logger: Logger,
    private val giveawaysRepository: GiveawaysRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GiveawaysScreenData())
    val uiState: StateFlow<GiveawaysScreenData> = _uiState.asStateFlow()

    private val parametersFlow = MutableStateFlow<GiveawaySearchParameters?>(null)
    private val refreshOutcomeFlow = MutableStateFlow<RefreshOutcome>(RefreshOutcome.Idle)


    init {
        viewModelScope.launch {
            val giveawaysFlow = parametersFlow
                .flatMapLatest { params ->
                    val source = if (params == null) flow { emitAll(giveawaysRepository.observeGiveaways()) }
                    else flow { emitAll(giveawaysRepository.observeGiveaways(params)) }
                    source.catch {
                        refreshOutcomeFlow.value = RefreshOutcome.Error
                        emit(emptyList())
                    }
                }

            combine(giveawaysFlow, refreshOutcomeFlow) { giveaways, outcome ->
                when (outcome) {
                    is RefreshOutcome.Error -> GiveawaysScreenData(
                        status = GiveawaysScreenStatus.ERROR,
                        giveaways = giveaways.toImmutableList()
                    )
                    RefreshOutcome.Idle -> GiveawaysScreenData(
                        status = GiveawaysScreenStatus.SUCCESS,
                        giveaways = giveaways.toImmutableList()
                    )
                }
            }
                .logFlow(logger)
                .collect { newState -> _uiState.emit(newState) }
        }
    }

    fun reloadGiveaways() {
        viewModelScope.launch {
            flow<Unit> {
                refreshOutcomeFlow.value = RefreshOutcome.Idle
                _uiState.update { current -> current.copy(status = GiveawaysScreenStatus.LOADING) }
                giveawaysRepository.refreshGiveaways()
            }
                .logFlow(logger)
                .catch { refreshOutcomeFlow.value = RefreshOutcome.Error }
                .collect { }
        }
    }

    fun loadGiveaway(parameters: GiveawaySearchParameters) {
        parametersFlow.value = parameters
    }


    @Immutable
    data class GiveawaysScreenData(
        val status: GiveawaysScreenStatus = GiveawaysScreenStatus.LOADING,
        val giveaways: ImmutableList<Giveaway> = persistentListOf()
    )


    internal enum class GiveawaysScreenStatus {
        LOADING, ERROR, SUCCESS
    }

    private sealed interface RefreshOutcome {
        data object Idle : RefreshOutcome
        data object Error : RefreshOutcome
    }
}
