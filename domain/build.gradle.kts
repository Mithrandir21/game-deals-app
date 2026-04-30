plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrains.kotlin)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "pm.bam.gamedeals.domain"
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
    implementation(project(":remote:cheapshark"))
    implementation(project(":remote:gamerpower"))

    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.coroutines)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.compiler)
    kapt(libs.hilt.androidx.compiler)

    implementation(libs.room)
    implementation(libs.room.runtime)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.androidx.paging)

    testImplementation(project(":testing"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.testing)
    testImplementation(libs.core.testing)
}