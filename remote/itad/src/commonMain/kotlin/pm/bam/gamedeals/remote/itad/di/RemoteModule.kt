package pm.bam.gamedeals.remote.itad.di

import org.koin.dsl.module
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.remote.itad.ItadAccountSourceImpl
import pm.bam.gamedeals.remote.itad.ItadSourceImpl

/**
 * Binds the ITAD implementations of the domain source ports (epic #205 / #219).
 *
 * [DealsSource] is the live deal source since Phase 2b; its last `get()` resolves the `RegionRepository`
 * (from `domainModule`) for regional pricing (Phase 3b, #212). [ItadAccountSource] is the user-scoped
 * account port (epic #219, Phase 2.3) over the bearer (OAuth) client.
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
            get(),
        )
    }

    single<ItadAccountSource> { ItadAccountSourceImpl(get(), get(), get(), get(), get()) }
}
