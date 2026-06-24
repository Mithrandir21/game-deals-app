package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Resolves a single [Giveaway] by id for the in-app detail screen (GamerPower source — one game per
 * giveaway). The giveaway is read from the same Room cache the list uses, so no network call is
 * needed. A missing id, or a giveaway no longer cached, surfaces as [GiveawayDetailScreenData.Error].
 * [Data.endDateMillis] is the parsed expiry (UTC) for the live countdown, or `null` for "no expiry".
 */
internal class GiveawayDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val giveawaysRepository: GiveawaysRepository,
    private val datetimeParsing: DatetimeParsing,
) : ViewModel() {

    private val giveawayId: Int? = savedStateHandle.get<Int>("giveawayId")

    val uiState: StateFlow<GiveawayDetailScreenData>
        field = MutableStateFlow<GiveawayDetailScreenData>(GiveawayDetailScreenData.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState.value = GiveawayDetailScreenData.Loading
            val id = giveawayId
            if (id == null) {
                uiState.value = GiveawayDetailScreenData.Error
                return@launch
            }
            try {
                val giveaway = giveawaysRepository.getGiveaway(id)
                if (giveaway == null) {
                    uiState.value = GiveawayDetailScreenData.Error
                    return@launch
                }
                uiState.value = GiveawayDetailScreenData.Data(
                    giveaway = giveaway,
                    endDateMillis = parseGiveawayEndDateMillis(giveaway.endDate, datetimeParsing),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fatal(logger, t)
                uiState.value = GiveawayDetailScreenData.Error
            }
        }
    }

    sealed class GiveawayDetailScreenData {
        data object Loading : GiveawayDetailScreenData()
        data object Error : GiveawayDetailScreenData()

        @Immutable
        data class Data(
            val giveaway: Giveaway,
            val endDateMillis: Long?,
        ) : GiveawayDetailScreenData()
    }
}
