package pm.bam.gamedeals.feature.game.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel

val gameModule = module {
    // The single Game Page ViewModel.
    viewModel { GamePageViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
