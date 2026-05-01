plugins {
    alias(libs.plugins.gamedeals.android.library)
    alias(libs.plugins.gamedeals.android.library.compose)
    alias(libs.plugins.gamedeals.android.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "pm.bam.gamedeals.common"
}

dependencies {
    // KotlinX Serialization - api so that other modules can use this dependency
    api(libs.kotlinx)
    api(libs.kotlinx.properties)

    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.coroutines)

    implementation(project(":logging"))

    testImplementation(libs.junit)
<<<<<<< wave/2026-05-01-bug-hunt/issue-42-storage-suspend
    testImplementation(libs.mockk)
=======
>>>>>>> dev
    testImplementation(libs.coroutines.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
