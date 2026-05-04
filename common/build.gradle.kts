import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx)
            api(libs.kotlinx.properties)
            api(libs.kotlinx.datetime)
            implementation(libs.coroutines)
            implementation(libs.koin.core)

            implementation(project(":logging"))
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
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
            }
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.common"
}
