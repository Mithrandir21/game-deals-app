package pm.bam.gamedeals.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Turns "Home scrolling feels janky" into numbers via [FrameTimingMetric] (P50/P90/P99 frame
 * durations). Run: `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 *
 * Two cases make the baseline-profile win measurable:
 *  - [scrollNoCompilation] — JIT-cold worst case, comparable to the un-profiled build.
 *  - [scrollBaselineProfile] — what production users get once the profile is installed.
 *
 * [scrollBaselineProfile] uses `CompilationMode.Partial()`, which requires the profile to already be
 * generated (run `:app:generateBaselineProfile` first). Compare frameDurationCpuMs between the two.
 */
@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollNoCompilation() = scroll(CompilationMode.None())

    @Test
    fun scrollBaselineProfile() = scroll(CompilationMode.Partial())

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        scrollHomeFeed()
    }
}
