plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrains.kotlin)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "pm.bam.gamedeals.remote"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = true // Enable by adding below line for a module that needs it
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

    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.coroutines)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.okio)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.retrofit)
    implementation(libs.sandwich)
    implementation(libs.sandwich.serializer)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.testing)
}