import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.gamedeals.kmp.library.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Compose runtime must be on every target's classpath when the Compose
            // Compiler plugin is applied (Phase 0 lesson). CMP's `compose.runtime`
            // resolves to the multiplatform Compose runtime artifact for each target.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation(project(":common"))
            implementation(project(":domain"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        androidMain.dependencies {
            implementation(project(":logging"))

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
            implementation(libs.androidx.ui.tooling)
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.material3.window)
            implementation(libs.androidx.compose.material3.adaptive)

            implementation(libs.coil3)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(project(":testing"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
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
    namespace = "pm.bam.gamedeals.common.ui"
}

dependencies {
    debugImplementation(libs.androidx.compose.test)
}
