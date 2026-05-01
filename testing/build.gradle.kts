plugins {
    alias(libs.plugins.gamedeals.android.library)
    alias(libs.plugins.gamedeals.android.ksp)
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

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.junit)
    implementation(libs.mockk)
    implementation(libs.hilt.testing)
    implementation(libs.androidx.runner)
    implementation(libs.coroutines.testing)
}
