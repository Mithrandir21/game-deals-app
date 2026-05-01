plugins {
    alias(libs.plugins.gamedeals.android.feature)
}

android {
    namespace = "pm.bam.gamedeals.feature.search"
}

dependencies {
    implementation(project(":logging"))
    implementation(project(":domain"))
    implementation(project(":common"))
    implementation(project(":common:ui"))
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(project(":testing"))
}
