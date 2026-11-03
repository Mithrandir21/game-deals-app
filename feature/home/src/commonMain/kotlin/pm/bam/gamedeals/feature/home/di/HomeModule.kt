package pm.bam.gamedeals.feature.home.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.home.ui.HomeViewModel
import pm.bam.gamedeals.feature.home.ui.WinterSaleEntryViewModel
import pm.bam.gamedeals.feature.home.ui.WinterSaleGuideViewModel

val homeModule = module {
    viewModel { WinterSaleEntryViewModel(get()) }
    viewModel { WinterSaleGuideViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
