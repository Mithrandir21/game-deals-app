import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx)
            implementation(libs.coroutines)
            implementation(libs.kotlinx.collections.immutable)

            // :remote api-exposes ktor-client-core + content-negotiation + logging +
            // ktor-serialization-kotlinx-json + sandwich-ktor for downstream use.
            api(project(":remote"))

            implementation(libs.koin.core)

            implementation(project(":logging"))
            implementation(project(":common"))
            implementation(project(":domain"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)

            implementation(libs.koin.android)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(project(":testing"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
                implementation(libs.ktor.client.mock)
            }
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.remote.cheapshark"

    buildFeatures {
        buildConfig = true
    }
}
