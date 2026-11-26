package pm.bam.gamedeals.common.ui.signals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import pm.bam.gamedeals.domain.models.GameSignals
import pm.bam.gamedeals.domain.models.rareDiscount
import pm.bam.gamedeals.domain.repositories.signals.GameSignalsRepository

/**
 * Where rows get per-game signals (the "Rare" badge, review scores). The app hosts provide the real repository; the
 * default has none, so previews and screen tests render rows without signals and need no DI.
 */
val LocalGameSignals = staticCompositionLocalOf<GameSignalsRepository> { NoGameSignals }

private object NoGameSignals : GameSignalsRepository {
    override suspend fun getSignals(gameIds: Collection<String>): Map<String, GameSignals> = emptyMap()
}

/**
 * Signals for [gameIds], loaded once per distinct set and kept while the set changes (a list growing by a page keeps
 * the rows it already had). Games the Worker is still fetching are asked for once more after [RECHECK_MS]. Empty
 * until loaded; never fails (rows just show no signals).
 */
@Composable
fun rememberGameSignals(gameIds: List<String>): Map<String, GameSignals> {
    val repository = LocalGameSignals.current
    val key = remember(gameIds) { gameIds.distinct() }
    var signals by remember(repository) { mutableStateOf<Map<String, GameSignals>>(emptyMap()) }
    LaunchedEffect(repository, key) {
        if (key.isEmpty()) return@LaunchedEffect
        signals = signals + repository.getSignals(key)
        if (key.any { it !in signals }) {
            delay(RECHECK_MS)
            signals = signals + repository.getSignals(key)
        }
    }
    return signals
}

/** A little longer than the repository waits before re-asking about a pending game. */
private const val RECHECK_MS = 65_000L

/** True when [gameId]'s deal at [cutPercent] off is a rare discount (see `GameSignals.rareDiscount`). */
fun Map<String, GameSignals>.isRare(gameId: String?, cutPercent: Int): Boolean =
    gameId != null && this[gameId]?.rareDiscount(cutPercent) != null

/** Signals loaded for the rows below a [ProvideGameSignals], for row helpers that don't take them as a parameter. */
val LocalRowSignals = compositionLocalOf<Map<String, GameSignals>> { emptyMap() }

/** Loads signals for [gameIds] once ([rememberGameSignals]) and hands them to every row in [content]. */
@Composable
fun ProvideGameSignals(gameIds: List<String>, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRowSignals provides rememberGameSignals(gameIds), content = content)
}
