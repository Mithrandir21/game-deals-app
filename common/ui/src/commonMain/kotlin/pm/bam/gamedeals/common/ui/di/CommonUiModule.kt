package pm.bam.gamedeals.common.ui.di

import org.koin.dsl.module
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.common.ui.share.DefaultDealShareTextBuilder

val commonUiModule = module {
    single<DealShareTextBuilder> { DefaultDealShareTextBuilder() }
}
