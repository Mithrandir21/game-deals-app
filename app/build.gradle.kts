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

    // IGDB credentials — read from the same local.properties (dev) / env-var (CI) sources as the signing keys.
    var igdbClientId = ""
    var igdbClientSecret = ""

    // IsThereAnyDeal (ITAD) API key — same local.properties (dev) / env-var (CI) sources (epic #205).
    var itadApiKey = ""

    // ITAD OAuth client id — for the account feature (epic #219, Phase 2). Same sources as the API key.
    var itadOauthClientId = ""

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

        igdbClientId = localProperties.getProperty("igdbClientId") ?: ""
        igdbClientSecret = localProperties.getProperty("igdbClientSecret") ?: ""

        itadApiKey = localProperties.getProperty("itadApiKey") ?: ""
        itadOauthClientId = localProperties.getProperty("itadOauthClientId") ?: ""
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

    // Env-var fallback for IGDB creds is independent of the signing block so CI can provide IGDB creds without a release key.
    if (igdbClientId.isEmpty()) igdbClientId = System.getenv("IGDB_CLIENT_ID") ?: ""
    if (igdbClientSecret.isEmpty()) igdbClientSecret = System.getenv("IGDB_CLIENT_SECRET") ?: ""

    // Env-var fallback for the ITAD key (independent of the signing block, like the IGDB creds).
    if (itadApiKey.isEmpty()) itadApiKey = System.getenv("ITAD_API_KEY") ?: ""
    if (itadOauthClientId.isEmpty()) itadOauthClientId = System.getenv("ITAD_OAUTH_CLIENT_ID") ?: ""

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

        buildConfigField("String", "IGDB_CLIENT_ID", "\"$igdbClientId\"")
        buildConfigField("String", "IGDB_CLIENT_SECRET", "\"$igdbClientSecret\"")
        buildConfigField("String", "ITAD_API_KEY", "\"$itadApiKey\"")
        buildConfigField("String", "ITAD_OAUTH_CLIENT_ID", "\"$itadOauthClientId\"")
    }

    buildFeatures.buildConfig = true

    buildTypes {
        debug {
            // Pair flag for the KMP-library `enableCoverage = true`; the root `jacocoAndroidTestReport` task folds the resulting `coverage.ec`
            // files into the parallel JaCoCo report alongside Kover's JVM coverage.
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

    // Remote source adapters — wired in Koin at the app boundary so :domain stays free of pm.bam.gamedeals.remote.* imports (port/adapter pattern).
    implementation(project(":remote:gamerpower"))
    implementation(project(":remote:igdb"))
    implementation(project(":remote:itad"))

    implementation(project(":feature:home"))
    implementation(project(":feature:game"))
    implementation(project(":feature:store"))
    implementation(project(":feature:search"))
    implementation(project(":feature:webview"))
    implementation(project(":feature:giveaways"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:bundles"))
    implementation(project(":feature:account"))

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
    androidTestImplementation(project(":remote:itad"))
    androidTestImplementation(project(":remote:gamerpower"))

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.compose.test)
}
