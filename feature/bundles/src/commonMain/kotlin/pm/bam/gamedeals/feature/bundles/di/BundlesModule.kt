package pm.bam.gamedeals.feature.bundles.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.bundles.ui.BundleDetailViewModel
import pm.bam.gamedeals.feature.bundles.ui.BundlesViewModel

val bundlesModule = module {
    viewModel { BundlesViewModel(get(), get(), get()) }
    viewModel { BundleDetailViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}
