package pm.bam.gamedeals.feature.deals.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.deals.ui.DealsViewModel

val dealsModule = module {
    viewModel { DealsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
