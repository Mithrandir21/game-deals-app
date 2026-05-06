import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.feature)
    alias(libs.plugins.mokkery)
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            // Pulled in by libs.androidx.espresso.device — see
            // https://github.com/android/android-test/issues/1755#issuecomment-1523810698
            implementation(libs.androidx.tracing)
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.androidx.espresso.device)
            }
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.feature.home"
}
