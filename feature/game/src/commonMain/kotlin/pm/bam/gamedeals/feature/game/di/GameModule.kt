package pm.bam.gamedeals.feature.game.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.game.ui.GameDetailsViewModel
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel
import pm.bam.gamedeals.feature.game.ui.GameViewModel

val gameModule = module {
    viewModel { GameViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { GameDetailsViewModel(get(), get(), get(), get(), get()) }
    // Unified Game Page (epic #291). Supersedes GameViewModel + GameDetailsViewModel; the old two are
    // removed once every caller is migrated (Phase 8).
    viewModel { GamePageViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
