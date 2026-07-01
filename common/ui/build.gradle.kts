plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.gamedeals.kmp.library.compose)
}

// This module's Gradle name is "ui", which collides with org.jetbrains.compose.ui:ui — both produce a
// klib whose unique_name is "ui_commonMain", triggering a KLIB resolver duplicate warning during the iOS
// metadata compile. A distinct archives name disambiguates the klib without renaming the module path.
base {
    archivesName.set("common-ui")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":logging"))
            implementation(project(":common"))
            implementation(project(":domain"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.koin.core)
            // koinInject() in shared composables (e.g. GamePeekSheet records deal-open analytics).
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.coil3)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)

            // Material3 Adaptive (JetBrains MP port) — currentWindowAdaptiveInfo() for rememberIsWideLayout().
            implementation(libs.compose.material3.adaptive)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.browser)
            implementation(libs.material)
            implementation(libs.androidx.compose.activity)
            implementation(libs.androidx.compose.navigation)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.androidx.ui)
            implementation(libs.androidx.ui.graphics)
            implementation(libs.androidx.ui.tooling.preview)
            implementation(libs.androidx.compose.material3)
        }

        val androidHostTest by getting {
            dependencies {
                implementation(project(":testing"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
            }
        }

        // Compose UI tests for shared components (e.g. GamePeekSheet) run on a device. The library
        // convention plugin enables `withDeviceTestBuilder` once `src/androidDeviceTest/` exists, but
        // unlike the feature plugin it doesn't auto-wire the test deps — so they're declared here.
        val androidDeviceTest by getting {
            dependencies {
                implementation(project(":testing"))
                implementation(libs.mockk.android)
                implementation(libs.androidx.junit)
                implementation(libs.androidx.runner)
                implementation(libs.androidx.espresso.core)
                implementation(libs.androidx.compose.junit4)
                implementation(libs.androidx.compose.test)
            }
        }
    }
}

