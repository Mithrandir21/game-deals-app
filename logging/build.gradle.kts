import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)
            // Sentry KMP iOS auto-links against Sentry-Cocoa. Until the Xcode
            // project wires that via SPM, keep Sentry on androidMain so the
            // iOS framework doesn't pull in unresolvable framework references.
            implementation(libs.sentry.kotlin.multiplatform)
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.logging"
}
