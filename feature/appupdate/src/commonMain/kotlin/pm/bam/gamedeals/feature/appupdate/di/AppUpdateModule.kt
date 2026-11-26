package pm.bam.gamedeals.feature.appupdate.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.appupdate.ui.AppUpdateViewModel

val appUpdateModule = module {
    viewModel { AppUpdateViewModel(get(), get(), get(), get(), get(), get()) }
}
