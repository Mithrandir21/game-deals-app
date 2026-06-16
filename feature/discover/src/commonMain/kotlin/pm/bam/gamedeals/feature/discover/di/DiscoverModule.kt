package pm.bam.gamedeals.feature.discover.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.discover.ui.DiscoverPickerViewModel
import pm.bam.gamedeals.feature.discover.ui.DiscoverResultsViewModel

val discoverModule = module {
    viewModel { DiscoverPickerViewModel(get(), get()) }
    // savedStateHandle is supplied by koin-compose-viewmodel.
    viewModel { DiscoverResultsViewModel(get(), get(), get()) }
}
