package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.Giveaway

/**
 * Single deep facade over the GamerPower remote API.
 *
 * Encapsulates Retrofit `*Api` wiring, transport DTOs, mappers, logging,
 * and exception transformation so that callers (repositories) only depend
 * on this interface and domain models.
 *
 * The implementation lives in `:remote:gamerpower`; this interface is the
 * domain-side port and only references domain types.
 */
interface GamerPowerSource {

    suspend fun fetchGiveaways(): List<Giveaway>
}
