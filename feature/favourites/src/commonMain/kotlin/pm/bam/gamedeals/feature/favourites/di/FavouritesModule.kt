package pm.bam.gamedeals.feature.favourites.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.favourites.ui.FavouritesViewModel

val favouritesModule = module {
    viewModel { FavouritesViewModel(get(), get()) }
}
