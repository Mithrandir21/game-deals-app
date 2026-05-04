package pm.bam.gamedeals.remote.gamerpower.di

import org.koin.core.qualifier.named
import org.koin.dsl.module
import pm.bam.gamedeals.domain.source.GamerPowerSource
import pm.bam.gamedeals.remote.gamerpower.GamerPowerSourceImpl

val GAMERPOWER_QUALIFIER = named("gamerpower")

val gamerpowerRemoteModule = module {
    single<GamerPowerSource> {
        GamerPowerSourceImpl(get(), get(), get(), get())
    }
}
