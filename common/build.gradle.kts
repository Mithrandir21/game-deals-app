plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrains.kotlin)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "pm.bam.gamedeals.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
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

    implementation(project(":logging"))

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}