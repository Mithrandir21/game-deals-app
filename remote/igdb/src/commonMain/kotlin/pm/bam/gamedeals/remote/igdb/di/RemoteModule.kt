package pm.bam.gamedeals.remote.igdb.di

import org.koin.core.qualifier.named
import org.koin.dsl.module
import pm.bam.gamedeals.domain.source.IgdbSource
import pm.bam.gamedeals.remote.igdb.IgdbSourceImpl

val IGDB_QUALIFIER = named("igdb")

val IGDB_TOKEN_QUALIFIER = named("igdb.token")

val igdbRemoteModule = module {
    single<IgdbSource> { IgdbSourceImpl(get(), get(), get()) }
}
