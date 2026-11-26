package pm.bam.gamedeals.remote.itad.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.remote.itad.auth.oauth.AndroidAuthBrowserLauncher
import pm.bam.gamedeals.remote.itad.auth.oauth.AuthBrowserLauncher

/**
 * Android-specific ITAD bindings (epic #219, Phase 2.2): the OAuth browser launcher. Register in the
 * Android composition root (`GameDealsApplication`).
 */
val itadAndroidModule = module {
    single<AuthBrowserLauncher> { AndroidAuthBrowserLauncher(androidContext()) }
}
