package pm.bam.gamedeals.feature.account.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.account.ui.AccountTabBadgeViewModel
import pm.bam.gamedeals.feature.account.ui.AccountViewModel
import pm.bam.gamedeals.feature.account.ui.CollectionListViewModel
import pm.bam.gamedeals.feature.account.ui.FollowedSeriesViewModel
import pm.bam.gamedeals.feature.account.ui.IgnoredViewModel
import pm.bam.gamedeals.feature.account.ui.MyNotesViewModel
import pm.bam.gamedeals.feature.account.ui.NotificationDayViewModel
import pm.bam.gamedeals.feature.account.ui.NotificationSettingsViewModel
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel
import pm.bam.gamedeals.feature.account.ui.SignInPromptViewModel
import pm.bam.gamedeals.feature.account.ui.WaitlistListViewModel

val accountModule = module {
    viewModel { AccountViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { WaitlistListViewModel(get(), get()) }
    viewModel { CollectionListViewModel(get(), get()) }
    viewModel { FollowedSeriesViewModel(get(), get(), get(), get(), get()) }
    viewModel { NotificationsViewModel(get(), get()) }
    viewModel { NotificationDayViewModel(get(), get(), get()) }
    viewModel { AccountTabBadgeViewModel(get(), get()) }
    viewModel { IgnoredViewModel(get(), get()) }
    viewModel { MyNotesViewModel(get(), get()) }
    viewModel { NotificationSettingsViewModel(get(), get()) }
    viewModel { SignInPromptViewModel(get(), get()) }
}
