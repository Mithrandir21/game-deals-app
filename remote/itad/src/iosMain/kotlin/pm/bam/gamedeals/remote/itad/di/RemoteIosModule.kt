package pm.bam.gamedeals.remote.itad.di

import org.koin.dsl.module
import pm.bam.gamedeals.remote.itad.auth.oauth.AuthBrowserLauncher
import pm.bam.gamedeals.remote.itad.auth.oauth.IosAuthBrowserLauncher

/**
 * iOS-specific ITAD bindings (epic #219, Phase 2.2): the OAuth browser launcher. Register in the iOS
 * composition root (`MainViewController`).
 */
val itadIosModule = module {
    single<AuthBrowserLauncher> { IosAuthBrowserLauncher() }
}
