import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":logging"))
            implementation(project(":domain"))
            implementation(project(":common"))
            implementation(project(":common:ui"))

            implementation(libs.coroutines)
            implementation(libs.androidx.lifecycle.viewmodel)
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(project(":testing"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
                implementation(libs.core.testing)
            }
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.feature.deal"
}
