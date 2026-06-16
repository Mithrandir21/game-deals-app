package pm.bam.gamedeals.feature.discover.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagDimension
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.repositories.discovery.TagDiscoveryRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/** Identifies one selectable tag chip (ids are only unique within a dimension). */
@Immutable
data class TagKey(val dimension: IgdbTagDimension, val igdbId: Long)

/** One dimension's section in the picker. */
@Immutable
data class TagGroup(val dimension: IgdbTagDimension, val tags: ImmutableList<IgdbTag>)

/**
 * Drives the tag-picker screen (epic #307, Phase 4). Loads the curated IGDB vocabulary (Room-cached),
 * groups it by dimension, and tracks the user's multi-select. The selected set is turned into an
 * [IgdbTagFilter] handed to the results screen on "Show results".
 */
internal class DiscoverPickerViewModel(
    private val logger: Logger,
    private val tagDiscoveryRepository: TagDiscoveryRepository,
) : ViewModel() {

    val uiState: StateFlow<PickerState>
        field = MutableStateFlow<PickerState>(PickerState.Loading)

    private var vocabulary: List<IgdbTag> = emptyList()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            uiState.value = PickerState.Loading
            runCatching { tagDiscoveryRepository.getTagVocabulary() }
                .onSuccess { vocab ->
                    vocabulary = vocab
                    emitReady(persistentSetOf())
                }
                .onFailure { t ->
                    fatal(logger, t) { "Failed to load tag vocabulary" }
                    uiState.value = PickerState.Error
                }
        }
    }

    fun retry() = load()

    /** Add/remove a tag from the multi-select. No-op until the vocabulary is loaded. */
    fun toggleTag(key: TagKey) {
        val current = (uiState.value as? PickerState.Ready)?.selected ?: return
        emitReady(if (key in current) current - key else current + key)
    }

    fun clear() {
        if (uiState.value is PickerState.Ready) emitReady(persistentSetOf())
    }

    /** The current selection as an AND-combined [IgdbTagFilter] for the results screen. */
    fun currentFilter(): IgdbTagFilter {
        val selected = (uiState.value as? PickerState.Ready)?.selected ?: persistentSetOf()
        fun ids(dimension: IgdbTagDimension) =
            selected.filter { it.dimension == dimension }.map { it.igdbId }.toImmutableList()
        return IgdbTagFilter(
            genreIds = ids(IgdbTagDimension.Genre),
            themeIds = ids(IgdbTagDimension.Theme),
            gameModeIds = ids(IgdbTagDimension.GameMode),
            perspectiveIds = ids(IgdbTagDimension.PlayerPerspective),
            keywordIds = ids(IgdbTagDimension.Keyword),
        )
    }

    private fun emitReady(selected: Set<TagKey>) {
        uiState.value = PickerState.Ready(groupByDimension(vocabulary), selected.toImmutableSet())
    }

    // Fixed dimension order (coarse → granular), tags sorted by name within each group.
    private fun groupByDimension(vocabulary: List<IgdbTag>): ImmutableList<TagGroup> =
        IgdbTagDimension.entries.mapNotNull { dimension ->
            val tags = vocabulary.filter { it.dimension == dimension }.sortedBy { it.name }
            if (tags.isEmpty()) null else TagGroup(dimension, tags.toImmutableList())
        }.toImmutableList()

    sealed interface PickerState {
        data object Loading : PickerState
        data object Error : PickerState

        @Immutable
        data class Ready(
            val groups: ImmutableList<TagGroup>,
            val selected: ImmutableSet<TagKey>,
        ) : PickerState
    }
}
