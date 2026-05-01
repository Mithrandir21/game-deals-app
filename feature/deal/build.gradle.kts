plugins {
    alias(libs.plugins.gamedeals.android.feature)
}

android {
    namespace = "pm.bam.gamedeals.feature.deal"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common:ui"))
    implementation(project(":logging"))
    implementation(project(":domain"))
    implementation(project(":feature:webview"))

    testImplementation(project(":testing"))
    testImplementation(libs.core.testing)
}
