package pm.bam.gamedeals.feature.settings.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.settings.ui.SettingsViewModel

val settingsModule = module {
    viewModel { SettingsViewModel(get(), get()) }
}
