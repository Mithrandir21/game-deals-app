package pm.bam.gamedeals.remote.igdb.di

import org.koin.core.qualifier.named
import org.koin.dsl.module

val IGDB_QUALIFIER = named("igdb")

val IGDB_TOKEN_QUALIFIER = named("igdb.token")

val igdbRemoteModule = module {
    // Phase 1 placeholder. Phase 2 binds IgdbSource here, mirroring cheapsharkRemoteModule.
}
