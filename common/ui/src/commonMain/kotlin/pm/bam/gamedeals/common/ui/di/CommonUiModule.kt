package pm.bam.gamedeals.common.ui.di

import org.koin.dsl.module
import pm.bam.gamedeals.common.ui.share.DealShareBuilder
import pm.bam.gamedeals.common.ui.share.DefaultDealShareBuilder

val commonUiModule = module {
    single<DealShareBuilder> { DefaultDealShareBuilder(get()) }
}
