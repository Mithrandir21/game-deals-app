package pm.bam.gamedeals

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Shared Android compile / packaging defaults for both library and application modules.
 *
 * Centralises the previously-duplicated `compileSdk`, `minSdk`, JDK 21 source/target
 * compatibility, the OSGi packaging-resource excludes (org.jspecify / okhttp logging
 * interceptor), and the `-XX:+EnableDynamicAgentLoading` JVM arg required by Mockk's
 * inline mock-maker on JDK 21+. Kotlin's `jvmToolchain(21)` is set per-plugin since
 * KMP and Android-only modules use different Kotlin extension types.
 *
 * Per project policy this stays purely structural: do not bump SDK levels here without
 * also coordinating an explicit version-bump PR.
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = 36

        defaultConfig.minSdk = 26

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }

        packaging.resources.excludes.addAll(sharedPackagingExcludes)
    }

    // Required by Mockk's inline mock-maker / byte-buddy agent attach on JDK 21+.
    tasks.withType(Test::class.java).configureEach {
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }
}
