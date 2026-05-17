plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.gamedeals.kmp.library.compose)
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

            implementation(libs.coil3)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
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
            implementation(libs.androidx.compose.material3.window)
            implementation(libs.androidx.compose.material3.adaptive)
        }

        val androidHostTest by getting {
            dependencies {
                implementation(project(":testing"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
            }
        }

        val androidDeviceTest by getting {
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

