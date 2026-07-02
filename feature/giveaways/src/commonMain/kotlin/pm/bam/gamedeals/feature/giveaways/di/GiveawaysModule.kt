package pm.bam.gamedeals.feature.giveaways.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.giveaways.ui.GiveawaysViewModel

val giveawaysModule = module {
    viewModel { GiveawaysViewModel(get(), get(), get(), get()) }
}
