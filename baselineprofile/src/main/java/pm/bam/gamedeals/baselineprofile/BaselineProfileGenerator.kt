package pm.bam.gamedeals.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile by exercising cold launch + Home scroll. The captured profile
 * lists the classes/methods on that path so ART AOT-compiles them at install time — which is what
 * removes cold-scroll jank in release (the gap the user is seeing on the un-profiled debug build).
 *
 * Run: `./gradlew :app:generateBaselineProfile` (the plugin builds :app's nonMinifiedRelease, runs
 * this generator on a connected device, and merges the result into :app so release/benchmark ship it).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = APP_PACKAGE,
        // Also emit a startup profile (dex-layout optimization for cold start). Its main payoff arrives once
        // :app enables R8/minification; harmless while minify is off, and it silences the generator warning.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        scrollHomeFeed()
    }
}
