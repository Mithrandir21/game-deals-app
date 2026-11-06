package pm.bam.gamedeals.feature.home.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.home.ui.HomeViewModel
import pm.bam.gamedeals.feature.home.ui.WinterSaleEntryViewModel
import pm.bam.gamedeals.feature.home.ui.WinterSaleGuideViewModel
import pm.bam.gamedeals.feature.home.ui.WinterSaleHubViewModel
import pm.bam.gamedeals.feature.home.ui.WinterSaleListViewModel
import pm.bam.gamedeals.feature.home.ui.WinterSalePeekViewModel

val homeModule = module {
    viewModel { WinterSaleEntryViewModel(get(), get(), get(), get()) }
    viewModel { WinterSalePeekViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { WinterSaleGuideViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { WinterSaleHubViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { WinterSaleListViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
