package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.IgdbGame

interface IgdbSource {
    suspend fun fetchSampleGames(): List<IgdbGame>
}
