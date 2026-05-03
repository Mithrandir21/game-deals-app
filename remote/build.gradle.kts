import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.gamedeals.kmp.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx)
            implementation(libs.coroutines)

            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.client.logging)
            api(libs.ktor.serialization.kotlinx.json)
            api(libs.sandwich.ktor)
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)

            implementation(libs.hilt.android)

            implementation(libs.ktor.client.okhttp)

            // :logging is still an Android-only module. :common is KMP but unused by
            // :remote/commonMain; keeping both dependencies on the Android side avoids
            // cross-module variant mismatch on iOS targets.
            implementation(project(":logging"))
            implementation(project(":common"))
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
                implementation(libs.ktor.client.mock)
            }
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.remote"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    add("kspAndroid", libs.hilt.compiler)
    add("kspAndroid", libs.hilt.androidx.compiler)
}
