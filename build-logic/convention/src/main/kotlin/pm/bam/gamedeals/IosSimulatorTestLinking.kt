package pm.bam.gamedeals

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import java.io.File

/**
 * Locations of the Swift Package Manager artifacts that Kotlin/Native iOS test binaries link against. `Package.resolved` inside `iosApp.xcodeproj` stays the
 * single source of truth for the sentry-cocoa version — nothing here pins it a second time.
 */
object IosSpm {
    /** Registered in the root `build.gradle.kts`: it names `iosApp.xcodeproj`, which is project shape rather than reusable convention. */
    const val RESOLVE_TASK_PATH = ":resolveIosSpmArtifacts"

    val isMacOs: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Mac")

    fun clonedPackagesDir(rootDir: File): File = File(rootDir, "build/spm")

    fun sentrySimulatorFrameworks(rootDir: File): File =
        File(clonedPackagesDir(rootDir), "artifacts/sentry-cocoa/Sentry/Sentry.xcframework/ios-arm64_x86_64-simulator")
}

/**
 * Gives `iosSimulatorArm64` test executables the framework and Swift-runtime search paths they need in order to link.
 *
 * Sentry KMP's cinterop klib carries `linkerOpts=-framework Sentry`, which every consumer inherits. The app framework gets away with it because it is static
 * and defers symbol resolution to Xcode, where SPM supplies Sentry; a standalone test executable has to resolve it at link time. Separately, the Sentry
 * artifacts auto-link Swift compatibility libraries through search paths baked in on their own build machines, so the local toolchain has to be
 * named explicitly. Without both, every module fails `linkDebugTestIosSimulatorArm64` and no iOS unit test can run at all.
 */
internal fun Project.configureIosSimulatorTestLinking() {
    if (!IosSpm.isMacOs) return

    val sentryFrameworks = IosSpm.sentrySimulatorFrameworks(rootDir).absolutePath
    // `providers.exec` keeps this configuration-cache safe, and `xcrun` resolves against the selected Xcode rather than a hardcoded /Applications path.
    val swiftRuntime = providers.exec { commandLine("xcrun", "--find", "swiftc") }
        .standardOutput.asText
        .map { File(it.trim()).parentFile.parentFile.resolve("lib/swift/iphonesimulator").absolutePath }
        .get()

    extensions.configure<KotlinMultiplatformExtension> {
        iosSimulatorArm64().binaries.withType(TestExecutable::class.java).configureEach {
            linkerOpts("-F", sentryFrameworks, "-L", swiftRuntime)
            linkTaskProvider.configure { dependsOn(IosSpm.RESOLVE_TASK_PATH) }
        }
    }
}
