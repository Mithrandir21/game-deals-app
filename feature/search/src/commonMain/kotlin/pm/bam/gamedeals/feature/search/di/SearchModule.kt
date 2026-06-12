package pm.bam.gamedeals.feature.search.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.search.ui.SearchViewModel

val searchModule = module {
    viewModel { SearchViewModel(get(), get(), get(), get(), get(), get()) }
}
