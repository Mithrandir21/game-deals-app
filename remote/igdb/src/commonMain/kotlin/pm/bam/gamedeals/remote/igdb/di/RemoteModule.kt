package pm.bam.gamedeals.remote.igdb.di

import org.koin.core.qualifier.named
import org.koin.dsl.module
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleSeriesSource
import pm.bam.gamedeals.domain.source.IgdbSource
import pm.bam.gamedeals.remote.igdb.IgdbSourceImpl
import pm.bam.gamedeals.remote.igdb.IgdbWinterSaleSeriesSource

val IGDB_QUALIFIER = named("igdb")

val IGDB_TOKEN_QUALIFIER = named("igdb.token")

val igdbRemoteModule = module {
    single<IgdbSource> { IgdbSourceImpl(get(), get(), get(), get()) }
    single<WinterSaleSeriesSource> { IgdbWinterSaleSeriesSource(get()) }
}
