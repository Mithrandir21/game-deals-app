package pm.bam.gamedeals.feature.account.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.account.ui.AccountViewModel
import pm.bam.gamedeals.feature.account.ui.CollectionListViewModel
import pm.bam.gamedeals.feature.account.ui.WaitlistListViewModel

val accountModule = module {
    viewModel { AccountViewModel(get(), get(), get(), get(), get()) }
    viewModel { WaitlistListViewModel(get(), get()) }
    viewModel { CollectionListViewModel(get(), get()) }
}
