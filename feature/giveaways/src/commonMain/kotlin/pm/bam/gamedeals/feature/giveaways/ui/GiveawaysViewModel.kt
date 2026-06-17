package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.logging.Logger


@OptIn(ExperimentalCoroutinesApi::class)
internal class GiveawaysViewModel(
    private val logger: Logger,
    private val giveawaysRepository: GiveawaysRepository,
    private val datetimeParsing: DatetimeParsing,
    private val clock: Clock,
) : ViewModel() {

    private val parametersFlow = MutableStateFlow<GiveawaySearchParameters?>(null)
    private val refreshOutcomeFlow = MutableStateFlow<RefreshOutcome>(RefreshOutcome.Idle)
    private val loadingFlow = MutableStateFlow(false)

    private val giveawaysFlow = parametersFlow
        .flatMapLatest { params ->
            val source = if (params == null) flow { emitAll(giveawaysRepository.observeGiveaways()) }
            else flow { emitAll(giveawaysRepository.observeGiveaways(params)) }
            source.catch {
                refreshOutcomeFlow.value = RefreshOutcome.Error
                emit(emptyList())
            }
        }
        // Any Room emission means fresh data has arrived — clear the in-flight reload gate.
        // Errors are preserved separately on refreshOutcomeFlow.
        .onEach { loadingFlow.value = false }

    val uiState: StateFlow<GiveawaysScreenData> = combine(
        giveawaysFlow,
        refreshOutcomeFlow,
        loadingFlow,
    ) { giveaways, outcome, loading ->
        val status = when {
            outcome is RefreshOutcome.Error -> GiveawaysScreenStatus.ERROR
            loading -> GiveawaysScreenStatus.LOADING
            else -> GiveawaysScreenStatus.SUCCESS
        }

        // Show only live giveaways — expired ones can no longer be claimed. The live filter runs AFTER
        // the repository's platform/type/sort filtering, so it composes with the filter sheet without
        // changing the repository contract. Each item's parsed expiry (UTC) feeds both the live filter
        // and the per-card countdown chip.
        val now = clock.nowMillis()
        val live = giveaways.map { it to parseGiveawayEndDateMillis(it.endDate, datetimeParsing) }
            .filter { (giveaway, endMs) -> isLive(giveaway, endMs, now) }

        GiveawaysScreenData(
            status = status,
            giveaways = live.map { it.first }.toImmutableList(),
            endDateMillis = live.mapNotNull { (giveaway, endMs) -> endMs?.let { giveaway.id to it } }
                .toMap()
                .toImmutableMap(),
        )
    }
        .logFlow(logger)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GiveawaysScreenData())

    fun reloadGiveaways() {
        viewModelScope.launch {
            loadingFlow.value = true
            refreshOutcomeFlow.value = RefreshOutcome.Idle
            try {
                giveawaysRepository.refreshGiveaways()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                refreshOutcomeFlow.value = RefreshOutcome.Error
            }
        }
    }

    fun loadGiveaway(parameters: GiveawaySearchParameters) {
        parametersFlow.value = parameters
    }


    @Immutable
    data class GiveawaysScreenData(
        val status: GiveawaysScreenStatus = GiveawaysScreenStatus.LOADING,
        val giveaways: ImmutableList<Giveaway> = persistentListOf(),
        /** Parsed expiry (epoch ms) per giveaway id, for the per-card countdown; absent = no expiry. */
        val endDateMillis: ImmutableMap<Int, Long> = persistentMapOf(),
    )


    internal enum class GiveawaysScreenStatus {
        LOADING, ERROR, SUCCESS
    }

    private sealed interface RefreshOutcome {
        data object Idle : RefreshOutcome
        data object Error : RefreshOutcome
    }
}
