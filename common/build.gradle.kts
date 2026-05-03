import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(21)

    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx)
            api(libs.kotlinx.properties)
            implementation(libs.coroutines)
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)

            implementation(libs.hilt.android)
            implementation(libs.hilt.navigation.compose)

            implementation(project(":logging"))
        }

        // Phase 0 keeps the existing JVM/MockK test stack on the Android target.
        // commonTest is intentionally empty until Phase 4 (Koin/Ktor test infra).
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.coroutines.testing)
            }
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes.named("release").configure {
        isMinifyEnabled = false
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }

    packaging.resources.apply {
        excludes += "/META-INF/LICENSE.md"
        excludes += "/META-INF/LICENSE-notice.md"
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
}

dependencies {
    add("kspAndroid", libs.hilt.compiler)
    add("kspAndroid", libs.hilt.androidx.compiler)
}

// Required by Mockk's inline mock-maker / byte-buddy agent attach on JDK 21+.
tasks.withType(Test::class.java).configureEach {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}
