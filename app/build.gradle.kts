import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.gamedeals.android.application)
    // Consumes the :baselineprofile producer and merges its generated profile into release/benchmark variants.
    alias(libs.plugins.androidx.baselineprofile)
    // Uploads the R8 mapping file so release crash stack traces are de-obfuscated in Sentry (see the sentry {} block).
    alias(libs.plugins.sentry.android)
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

    // Sentry DSN — crash/telemetry ingest endpoint. Same local.properties (dev) / env-var (CI) sources as the
    // other secrets. It's technically a client-side value (shipped in every build), but routed through the same
    // pipeline to keep it out of git and let release CI inject it. Blank in debug builds, where Sentry init no-ops.
    var sentryDsn = ""

    // First check if the local.properties file exists, meaning non-CI environment.
    if (File(rootProject.rootDir, "local.properties").exists()) {
        // Loading local properties so that we can use them in the build.gradle.kts file and not expose them in the repository
        val localProperties = Properties().apply { load(FileInputStream(File(rootProject.rootDir, "local.properties"))) }

        // Only use the real upload keystore when it's actually present. On a dev box that has local.properties
        // (for the IGDB/ITAD creds below) but no keystore, fall back to debug signing so `installRelease` and
        // Baseline Profile generation (nonMinifiedRelease/benchmarkRelease initWith release) work keystore-free.
        if (File(rootProject.rootDir, "upload_keystore.jks").exists()) {
            releaseKeyPresent = true
            releaseSigningKey = "release"
            releaseKeyAlias = localProperties.getProperty("keyAlias") ?: "FakeAlias"
            releaseKeyPassword = localProperties.getProperty("keyPassword") ?: "FakePassword"
            releaseKeyStoreFile = "../upload_keystore.jks"
            releaseKeyStorePassword = localProperties.getProperty("storePassword") ?: "FakeStorePassword"
        }

        igdbClientId = localProperties.getProperty("igdbClientId") ?: ""
        igdbClientSecret = localProperties.getProperty("igdbClientSecret") ?: ""

        itadApiKey = localProperties.getProperty("itadApiKey") ?: ""
        itadOauthClientId = localProperties.getProperty("itadOauthClientId") ?: ""

        sentryDsn = localProperties.getProperty("sentryDsn") ?: ""
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

    // Env-var fallback for the Sentry DSN (independent of the signing block, like the other creds).
    if (sentryDsn.isEmpty()) sentryDsn = System.getenv("SENTRY_DSN") ?: ""

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
        // API 35 is Google Play's current minimum target; matches compileSdk (36) to avoid a near-term re-bump.
        // NOTE: targeting 35+ forces edge-to-edge — see enableEdgeToEdge() in MainActivity and per-screen insets.
        targetSdk = 36
        // versionCode/versionName default to the last manually-set values for local/dev builds.
        // Release CI (Bitrise) overrides them via env: VERSION_NAME from the git tag (v1.0.7 -> 1.0.7)
        // and VERSION_CODE derived deterministically from the tag (major*10000 + minor*100 + patch ->
        // 10007) so it is reproducible and strictly increasing. Reading env directly here keeps the
        // override independent of the local.properties-vs-env signing branch above. See bitrise.yml.
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 10
        versionName = System.getenv("VERSION_NAME") ?: "1.0.7"

        testInstrumentationRunner = "pm.bam.gamedeals.KoinTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "IGDB_CLIENT_ID", "\"$igdbClientId\"")
        buildConfigField("String", "IGDB_CLIENT_SECRET", "\"$igdbClientSecret\"")
        buildConfigField("String", "ITAD_API_KEY", "\"$itadApiKey\"")
        buildConfigField("String", "ITAD_OAUTH_CLIENT_ID", "\"$itadOauthClientId\"")
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    buildFeatures.buildConfig = true

    buildTypes {
        debug {
            // Pair flag for the KMP-library `enableCoverage = true`; the root `jacocoAndroidTestReport` task folds the resulting `coverage.ec`
            // files into the parallel JaCoCo report alongside Kover's JVM coverage.
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = signingConfigs.getByName(releaseSigningKey)
        }
    }
}

// Sentry Android Gradle plugin — used ONLY to upload the R8 mapping so release crash traces are
// de-obfuscated. The SDK itself is the Sentry KMP artifact (initialised in GameDealsApplication), so:
//   - autoInstallation is OFF — we don't want the plugin pulling a second/clashing sentry-android.
//   - tracingInstrumentation is OFF — its bytecode transform would emit calls into sentry-android-okhttp,
//     which isn't on the classpath (the KMP SDK only brings sentry-android core/ndk/replay), and Ktor
//     builds its OkHttpClient internally so it wouldn't be caught anyway. Performance tracing instead
//     rides sentry-android-core's automatic activity/app-start transactions, gated by the tracesSampleRate
//     set in GameDealsApplication.
// org/project/authToken come from CI env. The upload only runs when a token is present, so local
// `assembleRelease` (no secrets) still generates the mapping and stays green.
sentry {
    org.set(System.getenv("SENTRY_ORG") ?: "")
    projectName.set(System.getenv("SENTRY_PROJECT") ?: "")
    authToken.set(System.getenv("SENTRY_AUTH_TOKEN") ?: "")

    autoInstallation { enabled.set(false) }
    tracingInstrumentation { enabled.set(false) }
    includeSourceContext.set(false)

    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(System.getenv("SENTRY_AUTH_TOKEN") != null)
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
    implementation(project(":feature:webview"))
    implementation(project(":feature:giveaways"))
    implementation(project(":feature:bundles"))
    implementation(project(":feature:account"))
    implementation(project(":feature:deals"))
    implementation(project(":feature:discover"))
    implementation(project(":feature:onboarding"))

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

    // Baseline Profile: profileinstaller applies the shipped ART profile on first run (so cold scroll is
    // AOT-compiled from launch); the baselineProfile config (added by the androidx.baselineprofile plugin)
    // pulls the profile generated by :baselineprofile into the release/benchmark variants.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

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
