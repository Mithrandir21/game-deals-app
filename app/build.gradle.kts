import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.gamedeals.android.application)
}

android {
    namespace = "pm.bam.gamedeals"

    // START - RELEASE SIGNING CONFIGURATION
    // Create variables to store the release key information
    var releaseKeyPresent = false
    var releaseSigningKey = "debug" // Default to debug key if release key is not present in environment.
    var releaseKeyAlias = ""
    var releaseKeyPassword = ""
    var releaseKeyStoreFile = ""
    var releaseKeyStorePassword = ""

    // First check if the local.properties file exists, meaning non-CI environment.
    if (File(rootProject.rootDir, "local.properties").exists()) {
        // Loading local properties so that we can use them in the build.gradle.kts file and not expose them in the repository
        val localProperties = Properties().apply { load(FileInputStream(File(rootProject.rootDir, "local.properties"))) }

        releaseKeyPresent = true
        releaseSigningKey = "release"
        releaseKeyAlias = localProperties.getProperty("keyAlias") ?: "FakeAlias"
        releaseKeyPassword = localProperties.getProperty("keyPassword") ?: "FakePassword"
        releaseKeyStoreFile = "../upload_keystore.jks"
        releaseKeyStorePassword = localProperties.getProperty("storePassword") ?: "FakeStorePassword"
    }
    // Check if environment variables are present, meaning CI environment.
    else if (System.getenv("RELEASE_KEY_ALIAS") != null) {
        releaseKeyPresent = true
        releaseSigningKey = "release"
        releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
        releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        releaseKeyStoreFile = "../upload_keystore.jks"
        releaseKeyStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
    }

    // If neither local.properties nor environment variables are present, the release key is not present.
    if(releaseKeyPresent) {
        signingConfigs {
            create("release") {
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeFile = file(releaseKeyStoreFile)
                storePassword = releaseKeyStorePassword
            }
        }
    }
    // END - RELEASE SIGNING CONFIGURATION


    defaultConfig {
        applicationId = "pm.bam.gamedeals"
        targetSdk = 34
        versionCode = 9
        versionName = "1.0.6"

        testInstrumentationRunner = "pm.bam.gamedeals.KoinTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            // Enable JaCoCo offline instrumentation for `:app` device tests so the
            // androidTest/ journey suite folds into Kover's aggregated report.
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))

            signingConfig = signingConfigs.getByName(releaseSigningKey)
        }
    }
}

dependencies {

    implementation(project(":logging"))
    implementation(project(":common"))
    implementation(project(":common:ui"))
    implementation(project(":domain"))

    // Remote source adapters — wired in Koin at the app boundary so :domain
    // stays free of pm.bam.gamedeals.remote.* imports (port/adapter pattern).
    implementation(project(":remote:cheapshark"))
    implementation(project(":remote:gamerpower"))

    implementation(project(":feature:home"))
    implementation(project(":feature:game"))
    implementation(project(":feature:store"))
    implementation(project(":feature:search"))
    implementation(project(":feature:webview"))
    implementation(project(":feature:giveaways"))
    implementation(project(":feature:favourites"))

    val composeBom = platform(libs.androidx.compose.bom)


    implementation(libs.androidx.ktx)
    implementation(libs.androidx.ktx.lifecycle.runtime)
    implementation(libs.androidx.compose.activity)
    implementation(composeBom)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.sentry.kotlin.multiplatform)

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window)
    implementation(libs.androidx.compose.material3.adaptive)

    implementation(libs.coil3)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.ktor)
    implementation(libs.coil3.test)

    testImplementation(project(":testing"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.kotlinx)
    androidTestImplementation(libs.koin.test)
    androidTestImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.room)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(project(":remote:cheapshark"))
    androidTestImplementation(project(":remote:gamerpower"))

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.compose.test)
}
