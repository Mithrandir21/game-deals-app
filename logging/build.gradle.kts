plugins {
    alias(libs.plugins.gamedeals.android.library)
}

android {
    namespace = "pm.bam.gamedeals.logging"
}

dependencies {
    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.sentry.kotlin.multiplatform)

    implementation(libs.koin.core)
}
