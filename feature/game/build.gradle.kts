plugins {
    alias(libs.plugins.gamedeals.android.feature)
}

android {
    namespace = "pm.bam.gamedeals.feature.game"
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":domain"))
    implementation(project(":common"))
    implementation(project(":common:ui"))
    implementation(project(":feature:deal"))
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.tracing) {
        because("Pulled in by libs.androidx.espresso.device — see " +
                "https://github.com/android/android-test/issues/1755#issuecomment-1523810698")
    }

    testImplementation(project(":testing"))
    testImplementation(libs.core.testing)
    androidTestImplementation(libs.androidx.espresso.device)
}
