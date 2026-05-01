plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrains.kotlin)
    alias(libs.plugins.compose)
}

android {
    namespace = "pm.bam.gamedeals.feature.webview"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"

            // Temporary fix for OSGi issue org.jspecify:jspecify:1.0.0 and com.squareup.okhttp3:logging-interceptor:5.2.1
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    tasks.withType<Test> {
        jvmArgs = listOf("-XX:+EnableDynamicAgentLoading")
    }
}

dependencies {

    implementation(project(":logging"))
    implementation(project(":common"))
    implementation(project(":common:ui"))


    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.webview)

    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}