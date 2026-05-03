import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.gamedeals.kmp.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(project(":logging"))
            implementation(project(":common"))

            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)

            implementation(libs.coroutines)
            implementation(libs.kotlinx.collections.immutable)

            implementation(libs.koin.core)
            implementation(libs.koin.android)

            implementation(libs.androidx.compose.runtime)

            implementation(libs.room)
            implementation(libs.room.runtime)
            implementation(libs.room.paging)

            implementation(libs.androidx.paging.common)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(project(":testing"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
                implementation(libs.core.testing)
                implementation(libs.kotlinx)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.domain"
}
