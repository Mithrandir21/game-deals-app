plugins {
    alias(libs.plugins.gamedeals.android.library)
    alias(libs.plugins.gamedeals.android.ksp)
}

android {
    namespace = "pm.bam.gamedeals.logging"
}

dependencies {
    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.sentry.kotlin.multiplatform)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.androidx.compiler)
}
