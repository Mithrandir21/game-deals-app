@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.discover.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagDimension
import pm.bam.gamedeals.domain.repositories.discovery.TagDiscoveryRepository
import pm.bam.gamedeals.feature.discover.ui.DiscoverPickerViewModel.PickerState
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoverPickerViewModelTest : MainDispatcherTest() {

    private val tagDiscoveryRepository: TagDiscoveryRepository = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel() = DiscoverPickerViewModel(TestingLoggingListener(), tagDiscoveryRepository)

    @Test
    fun loads_vocabulary_and_groups_by_dimension_in_fixed_order() = runTest {
        everySuspend { tagDiscoveryRepository.getTagVocabulary() } returns listOf(
            IgdbTag(IgdbTagDimension.Keyword, 270L, "roguelike", "roguelike"),
            IgdbTag(IgdbTagDimension.Genre, 5L, "Shooter", "shooter"),
            IgdbTag(IgdbTagDimension.Genre, 12L, "Role-playing (RPG)", "role-playing-rpg"),
        )
        val vm = createViewModel()
        advanceUntilIdle()

        val ready = vm.uiState.value as PickerState.Ready
        // Genre group precedes Keyword group (fixed dimension order); tags sorted by name within a group.
        assertEquals(IgdbTagDimension.Genre, ready.groups[0].dimension)
        assertEquals(listOf("Role-playing (RPG)", "Shooter"), ready.groups[0].tags.map { it.name })
        assertEquals(IgdbTagDimension.Keyword, ready.groups[1].dimension)
    }

    @Test
    fun toggleTag_updates_selection_and_currentFilter() = runTest {
        everySuspend { tagDiscoveryRepository.getTagVocabulary() } returns listOf(
            IgdbTag(IgdbTagDimension.Genre, 12L, "Role-playing (RPG)", "role-playing-rpg"),
            IgdbTag(IgdbTagDimension.Keyword, 270L, "roguelike", "roguelike"),
        )
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleTag(TagKey(IgdbTagDimension.Genre, 12L))
        vm.toggleTag(TagKey(IgdbTagDimension.Keyword, 270L))

        val selected = (vm.uiState.value as PickerState.Ready).selected
        assertTrue(TagKey(IgdbTagDimension.Genre, 12L) in selected)
        val filter = vm.currentFilter()
        assertEquals(listOf(12L), filter.genreIds)
        assertEquals(listOf(270L), filter.keywordIds)

        // Toggling again removes it.
        vm.toggleTag(TagKey(IgdbTagDimension.Genre, 12L))
        assertTrue(vm.currentFilter().genreIds.isEmpty())
    }

    @Test
    fun clear_empties_selection() = runTest {
        everySuspend { tagDiscoveryRepository.getTagVocabulary() } returns listOf(
            IgdbTag(IgdbTagDimension.Genre, 12L, "Role-playing (RPG)", "role-playing-rpg"),
        )
        val vm = createViewModel()
        advanceUntilIdle()
        vm.toggleTag(TagKey(IgdbTagDimension.Genre, 12L))

        vm.clear()

        assertTrue((vm.uiState.value as PickerState.Ready).selected.isEmpty())
        assertTrue(vm.currentFilter().isEmpty())
    }

    @Test
    fun vocabulary_load_failure_surfaces_error_state() = runTest {
        everySuspend { tagDiscoveryRepository.getTagVocabulary() } throws RuntimeException("IGDB down")
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(PickerState.Error, vm.uiState.value)
    }
}
