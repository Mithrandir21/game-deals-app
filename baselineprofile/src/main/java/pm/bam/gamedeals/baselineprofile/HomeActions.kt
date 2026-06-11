package pm.bam.gamedeals.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** Package of the app under test (matches :app applicationId). */
const val APP_PACKAGE = "pm.bam.gamedeals"

/**
 * Wait for the Home feed (its single LazyColumn) to appear, then fling it down and back up a few
 * times. This is the exact hot path we want AOT-compiled by the baseline profile and measured by
 * [androidx.benchmark.macro.FrameTimingMetric] — it mirrors the user's "scroll up/down is janky" report.
 */
fun MacrobenchmarkScope.scrollHomeFeed() {
    // The Home LazyColumn is the screen's only scrollable container.
    val feed = device.wait(Until.findObject(By.scrollable(true)), 10_000) ?: return
    // Keep flings away from the screen edges so we don't trigger system back/nav gestures.
    feed.setGestureMargin(device.displayWidth / 5)
    repeat(3) {
        feed.fling(Direction.DOWN)
        device.waitForIdle()
    }
    repeat(3) {
        feed.fling(Direction.UP)
        device.waitForIdle()
    }
}
