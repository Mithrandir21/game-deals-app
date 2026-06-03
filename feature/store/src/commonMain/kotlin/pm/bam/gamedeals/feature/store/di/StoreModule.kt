package pm.bam.gamedeals.feature.store.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.store.ui.StoreViewModel

val storeModule = module {
    viewModel { StoreViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
