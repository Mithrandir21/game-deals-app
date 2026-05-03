package pm.bam.gamedeals

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Replaces the production [GameDealsApplication] with [TestGameDealsApplication] for
 * instrumented test runs. Wired via `testInstrumentationRunner` in `:app/build.gradle.kts`.
 */
class KoinTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestGameDealsApplication::class.java.name, context)
}
