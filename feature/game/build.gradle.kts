plugins {
    alias(libs.plugins.gamedeals.kmp.feature)
    alias(libs.plugins.mokkery)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.zoomable)
            implementation(libs.vico.compose)
            implementation(libs.vico.compose.m3)
        }

        androidMain.dependencies {
            // Pulled in by libs.androidx.espresso.device — see
            // https://github.com/android/android-test/issues/1755#issuecomment-1523810698
            implementation(libs.androidx.tracing)
        }

        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.espresso.device)
            }
        }
    }
}

