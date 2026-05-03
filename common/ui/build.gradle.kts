plugins {
    alias(libs.plugins.gamedeals.android.library)
    alias(libs.plugins.gamedeals.android.library.compose)
}

android {
    namespace = "pm.bam.gamedeals.common.ui"
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":common"))
    implementation(project(":domain"))

    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.compose.activity)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.compose.runtime)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window)
    implementation(libs.androidx.compose.material3.adaptive)

    implementation(libs.coil3)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.ktor)

    testImplementation(project(":testing"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.testing)

    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.junit4)
    debugImplementation(libs.androidx.compose.test)
}
