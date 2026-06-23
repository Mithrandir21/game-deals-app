plugins {
    alias(libs.plugins.gamedeals.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            // Sentry KMP — shared bridge for Android + iOS (SentryLoggingListener / configureSentryOptions).
            // The iOS klib's cinterop symbols resolve at app-link: iosApp.xcodeproj links Sentry-Cocoa via SPM,
            // pinned to the cocoa version this KMP release was built against (see gradle/libs.versions.toml).
            implementation(libs.sentry.kotlin.multiplatform)
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)
        }
    }
}

