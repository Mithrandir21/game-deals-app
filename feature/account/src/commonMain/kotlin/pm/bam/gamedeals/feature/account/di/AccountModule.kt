package pm.bam.gamedeals.feature.account.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.account.ui.AccountViewModel

val accountModule = module {
    viewModel { AccountViewModel(get(), get(), get(), get()) }
}
