package pm.bam.gamedeals.remote.itad.di

import org.koin.dsl.module
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.remote.itad.ItadSourceImpl

/**
 * Binds the ITAD implementation of [DealsSource].
 *
 * NOT registered in the app yet (epic #205): Phase 1 builds and tests this module while CheapShark
 * stays the active source. Phase 2 ("swap DI + Room migration") registers this module and removes
 * `cheapsharkRemoteModule`'s [DealsSource] binding — registering both at once would clash.
 */
val itadRemoteModule = module {
    single<DealsSource> {
        ItadSourceImpl(
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
}
