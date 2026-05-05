import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines)
            implementation(libs.coroutines.testing)
            implementation(libs.kotlinx.collections.immutable)

            // Ktor MockEngine helper exposed to test source sets in consumer modules.
            api(libs.ktor.client.core)
            api(libs.ktor.client.mock)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.serialization.kotlinx.json)
            api(libs.kotlinx)

            implementation(project(":logging"))
            implementation(project(":common"))
            implementation(project(":domain"))
        }

    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.testing"
}
