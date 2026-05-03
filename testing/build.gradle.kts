plugins {
    alias(libs.plugins.gamedeals.android.library)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "pm.bam.gamedeals.testing"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":common"))

    implementation(libs.coroutines)

    implementation(libs.junit)
    implementation(libs.mockk)
    implementation(libs.androidx.runner)
    implementation(libs.coroutines.testing)
}
