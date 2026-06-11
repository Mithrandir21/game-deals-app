plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

// com.android.test module: the whole module IS an instrumented test, so its code lives in src/main.
// It produces :app's baseline profile and hosts the Home-scroll FrameTimingMetric benchmark. Nothing
// here ships in the release APK.
android {
    namespace = "pm.bam.gamedeals.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        // API 28+ is the well-supported floor for Baseline Profile generation (profman) and CompilationMode.
        minSdk = 28
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The app under test. The baselineprofile plugin builds :app's nonMinifiedRelease/benchmarkRelease
    // variants (both debug-signed here, so no release keystore is required) and drives them black-box.
    targetProjectPath = ":app"
}

// Match the project-wide JDK 21 toolchain (AGP 9 built-in Kotlin exposes the `kotlin` extension).
kotlin {
    jvmToolchain(21)
}

// Generate/measure on a physically connected device (the user's flagship). For CI, swap to a
// Gradle-managed virtual device via testOptions.managedDevices + baselineProfile.managedDevices.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}
