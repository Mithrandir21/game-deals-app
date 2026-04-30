package pm.bam.gamedeals.remote.gamerpower

import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveaway

/**
 * Single deep facade over the GamerPower remote API.
 *
 * Encapsulates Retrofit `*Api` wiring, logging, and exception transformation
 * so that callers (repositories) only depend on this interface.
 */
interface GamerPowerSource {

    suspend fun fetchGiveaways(): List<RemoteGiveaway>
}
