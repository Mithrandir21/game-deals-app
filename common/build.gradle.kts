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
            api(libs.kotlinx.properties)
            api(libs.kotlinx.datetime)
            implementation(libs.coroutines)
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)

            implementation(libs.hilt.android)
            implementation(libs.hilt.navigation.compose)

            implementation(project(":logging"))
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

dependencies {
    add("kspAndroid", libs.hilt.compiler)
    add("kspAndroid", libs.hilt.androidx.compiler)
}
