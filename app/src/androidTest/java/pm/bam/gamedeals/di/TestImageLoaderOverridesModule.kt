package pm.bam.gamedeals.di

import android.graphics.drawable.ColorDrawable
import coil.ImageLoader
import coil.test.FakeImageLoaderEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Test-only Koin module that replaces the production [ImageLoader] with a Coil 2
 * [FakeImageLoaderEngine]-backed loader. The production ImageLoader makes real network
 * requests for store banners/logos/icons against `cheapshark.com`; under instrumentation
 * those crossfade animations (and possible network retries) keep Compose's idling
 * resource busy, blowing the `composeRule.waitUntil` budget on the integration journey.
 *
 * Loaded last by [pm.bam.gamedeals.TestGameDealsApplication] so Koin's last-load-wins
 * semantics override `appModule`'s ImageLoader binding.
 */
val testImageLoaderOverridesModule = module {
    single<ImageLoader> {
        val fakeEngine = FakeImageLoaderEngine.Builder()
            .default(ColorDrawable(android.graphics.Color.GRAY))
            .build()
        ImageLoader.Builder(androidContext())
            .components { add(fakeEngine) }
            .build()
    }
}
