import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines)

            implementation(project(":logging"))
            implementation(project(":common"))
        }

        androidMain.dependencies {
            implementation(libs.junit)
            implementation(libs.mockk)
            implementation(libs.androidx.runner)
            implementation(libs.coroutines.testing)
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.testing"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
