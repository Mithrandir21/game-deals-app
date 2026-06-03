package pm.bam.gamedeals.remote.itad.di

import org.koin.dsl.module
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.remote.itad.ItadSourceImpl

/**
 * Binds the ITAD implementation of [DealsSource] (the live source since Phase 2b, epic #205).
 *
 * The last `get()` resolves the `RegionRepository` (from `domainModule`) that supplies the selected
 * `country` for regional pricing (Phase 3b, #212).
 */
val itadRemoteModule = module {
    single<DealsSource> {
        ItadSourceImpl(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
}
