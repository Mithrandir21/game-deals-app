package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.GameSignalsBatch

/** The Game Deals Worker's per-game signals endpoint. */
interface GameSignalsSource {
    /** Signals for up to [MAX_IDS] ITAD game ids. Throws on a failed call. */
    suspend fun fetch(gameIds: List<String>): GameSignalsBatch

    companion object {
        /** The most ids one call may carry; the Worker refuses more. */
        const val MAX_IDS = 50
    }
}
