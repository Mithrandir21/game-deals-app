plugins {
    alias(libs.plugins.gamedeals.android.feature)
}

android {
    namespace = "pm.bam.gamedeals.feature.store"
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":domain"))
    implementation(project(":common"))
    implementation(project(":common:ui"))
    implementation(libs.compose.material.icons)

    testImplementation(project(":testing"))
}
