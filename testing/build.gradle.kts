import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines)
            implementation(libs.coroutines.testing)
            implementation(libs.kotlinx.collections.immutable)

            implementation(project(":logging"))
            implementation(project(":common"))
            implementation(project(":domain"))
        }

    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.testing"
}
