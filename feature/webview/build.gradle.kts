plugins {
    alias(libs.plugins.gamedeals.android.library)
    alias(libs.plugins.gamedeals.android.library.compose)
}

android {
    namespace = "pm.bam.gamedeals.feature.webview"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":common"))
    implementation(project(":common:ui"))

    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.webview)

    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
