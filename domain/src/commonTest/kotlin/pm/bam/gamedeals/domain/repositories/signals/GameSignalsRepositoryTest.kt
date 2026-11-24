package pm.bam.gamedeals.domain.repositories.signals

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.GameSignals
import pm.bam.gamedeals.domain.models.GameSignalsBatch
import pm.bam.gamedeals.domain.source.GameSignalsSource
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.testing.FakeFeatureFlags
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameSignalsRepositoryTest {

    private fun id(n: Int) = "018d937f-0000-7000-8000-${n.toString().padStart(12, '0')}"
    private fun signals(id: String, score: Int = 90) = GameSignals(gameId = id, updatedEpochMs = 1L, weeklyMaxCut = listOf(0, 50), reviewScore = score)

    private var now = 1_000_000L
    private val clock = Clock { now }
    private val calls = mutableListOf<List<String>>()
    private var pending = emptySet<String>()
    private var score = 90

    private val source: GameSignalsSource = mock(MockMode.autoUnit) {
        everySuspend { fetch(any()) } calls { (ids: List<String>) ->
            calls += ids
            GameSignalsBatch(games = ids.filterNot { it in pending }.map { signals(it, score) }, pending = ids.filter { it in pending })
        }
    }

    private val flags = FakeFeatureFlags()
    private val repository = GameSignalsRepositoryImpl(source, clock, TestingLoggingListener(), flags)

    @Test
    fun switched_off_there_are_no_signals_and_no_worker_calls_even_from_memory() = runTest {
        repository.getSignals(listOf(id(1)))
        flags.set(FeatureFlag.GameSignals, false)

        assertEquals(emptyMap(), repository.getSignals(listOf(id(1), id(2))))
        assertEquals(1, calls.size)

        flags.set(FeatureFlag.GameSignals, true)
        assertEquals(setOf(id(1), id(2)), repository.getSignals(listOf(id(1), id(2))).keys)
    }

    @Test
    fun fetches_the_games_once_and_answers_from_memory_while_fresh() = runTest {
        val first = repository.getSignals(listOf(id(1), id(2), id(1)))
        assertEquals(setOf(id(1), id(2)), first.keys)
        assertEquals(listOf(listOf(id(1), id(2))), calls)

        now += GameSignalsRepository.FRESH_FOR_MS - 1
        repository.getSignals(listOf(id(1), id(2)))
        assertEquals(1, calls.size)
    }

    @Test
    fun stale_games_are_fetched_again() = runTest {
        repository.getSignals(listOf(id(1)))
        now += GameSignalsRepository.FRESH_FOR_MS
        score = 50
        val again = repository.getSignals(listOf(id(1)))
        assertEquals(2, calls.size)
        assertEquals(50, again.getValue(id(1)).reviewScore)
    }

    @Test
    fun only_missing_games_are_asked_for() = runTest {
        repository.getSignals(listOf(id(1)))
        repository.getSignals(listOf(id(1), id(2)))
        assertEquals(listOf(listOf(id(1)), listOf(id(2))), calls)
    }

    @Test
    fun calls_carry_at_most_fifty_ids() = runTest {
        val result = repository.getSignals((1..120).map(::id))
        assertEquals(listOf(50, 50, 20), calls.map { it.size })
        assertEquals(120, result.size)
    }

    @Test
    fun games_the_worker_is_still_fetching_are_absent_and_not_asked_again_for_a_minute() = runTest {
        pending = setOf(id(2))
        val first = repository.getSignals(listOf(id(1), id(2)))
        assertEquals(setOf(id(1)), first.keys)

        now += 30_000
        repository.getSignals(listOf(id(2)))
        assertEquals(1, calls.size, "too soon to ask again")

        now += 30_000
        pending = emptySet()
        val later = repository.getSignals(listOf(id(2)))
        assertEquals(setOf(id(2)), later.keys)
    }

    @Test
    fun a_failed_call_falls_back_to_an_older_copy_or_nothing() = runTest {
        repository.getSignals(listOf(id(1)))
        now += GameSignalsRepository.FRESH_FOR_MS
        everySuspend { source.fetch(any()) } throws IllegalStateException("offline")
        val result = repository.getSignals(listOf(id(1), id(2)))
        assertEquals(setOf(id(1)), result.keys)
    }

    @Test
    fun ids_that_are_not_itad_ids_are_ignored() = runTest {
        assertTrue(repository.getSignals(listOf("12345", "half-life", "")).isEmpty())
        verifySuspend(exactly(0)) { source.fetch(any()) }
    }
}
