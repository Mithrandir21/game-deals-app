package pm.bam.gamedeals.feature.discover.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.discover.ui.DiscoverPickerViewModel

val discoverModule = module {
    viewModel { DiscoverPickerViewModel(get(), get()) }
}
