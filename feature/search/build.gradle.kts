import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.gamedeals.kmp.library.compose)
    alias(libs.plugins.mokkery)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            implementation(project(":logging"))
            implementation(project(":domain"))
            implementation(project(":common"))
            implementation(project(":common:ui"))

            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.compose.navigation)

            implementation(libs.coil3)
            implementation(libs.coil3.compose)
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.ui)
            implementation(libs.androidx.ui.graphics)
            implementation(libs.androidx.ui.tooling)
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.material3.window)
            implementation(libs.androidx.compose.material3.adaptive)

            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            // Pulled in by libs.androidx.espresso.device — see
            // https://github.com/android/android-test/issues/1755#issuecomment-1523810698
            implementation(libs.androidx.tracing)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing"))
            implementation(libs.coroutines.testing)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
            }
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.mockk.android)
                implementation(libs.androidx.junit)
                implementation(libs.androidx.runner)
                implementation(libs.androidx.espresso.core)
                implementation(libs.androidx.compose.junit4)
            }
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.feature.search"

    @Suppress("UnstableApiUsage")
    testOptions.emulatorControl.enable = true

    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

compose.resources {
    publicResClass = true
    packageOfResClass = "pm.bam.gamedeals.feature.search.generated.resources"
    generateResClass = org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration.Auto
}

dependencies {
    debugImplementation(libs.androidx.compose.test)
}
