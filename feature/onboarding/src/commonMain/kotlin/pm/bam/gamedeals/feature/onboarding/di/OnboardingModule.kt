package pm.bam.gamedeals.feature.onboarding.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.feature.onboarding.platform.RegionDetector
import pm.bam.gamedeals.feature.onboarding.platform.deviceRegionDetector
import pm.bam.gamedeals.feature.onboarding.ui.OnboardingViewModel

val onboardingModule = module {
    single<RegionDetector> { deviceRegionDetector() }
    viewModel { OnboardingViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
