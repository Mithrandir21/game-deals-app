import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.gamedeals.kmp.ksp)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.androidx.room)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx)
            api(libs.kotlinx.collections.immutable)
            api(libs.kotlinx.datetime)
            api(libs.androidx.paging.common)

            implementation(libs.coroutines)
            implementation(libs.room.runtime.multiplatform)

            implementation(project(":common"))
        }

        androidMain.dependencies {
            implementation(project(":logging"))

            implementation(libs.koin.core)
            implementation(libs.koin.android)

            implementation(libs.room.runtime)
            implementation(libs.room.paging)
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

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.domain"
}
