package pm.bam.gamedeals

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Shared Android compile / packaging defaults for both library and application modules.
 *
 * Centralises the previously-duplicated `compileSdk`, `minSdk`, JDK 21 source/target
 * compatibility, the OSGi packaging-resource excludes (org.jspecify / okhttp logging
 * interceptor), and the `-XX:+EnableDynamicAgentLoading` JVM arg required by Mockk's
 * inline mock-maker on JDK 21+.
 *
 * Per project policy this stays purely structural: do not bump SDK levels here without
 * also coordinating an explicit version-bump PR.
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = 36

        defaultConfig {
            minSdk = 26
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }

        packaging {
            resources {
                excludes += "/META-INF/LICENSE.md"
                excludes += "/META-INF/LICENSE-notice.md"
                excludes += "/META-INF/{AL2.0,LGPL2.1}"

                // Temporary fix for OSGi issue org.jspecify:jspecify:1.0.0 and
                // com.squareup.okhttp3:logging-interceptor:5.2.1
                excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            }
        }
    }

    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        jvmToolchain(21)
    }

    // Required by Mockk's inline mock-maker / byte-buddy agent attach on JDK 21+.
    tasks.withType(Test::class.java).configureEach {
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }
}
