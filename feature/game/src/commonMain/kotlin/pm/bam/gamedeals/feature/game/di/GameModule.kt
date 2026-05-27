package pm.bam.gamedeals.feature.game.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.game.ui.GameDetailsViewModel
import pm.bam.gamedeals.feature.game.ui.GameViewModel

val gameModule = module {
    viewModel { GameViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { GameDetailsViewModel(get(), get(), get(), get(), get()) }
}
