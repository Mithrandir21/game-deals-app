plugins {
    alias(libs.plugins.gamedeals.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            // Sentry KMP — shared bridge for Android + iOS (SentryLoggingListener / configureSentryOptions).
            // ⚠️ iOS caveat: the iOS *link* step (Mac/Xcode only) needs the app target to link Sentry-Cocoa
            // via SPM. Until that's wired, an iOS build will fail to link; the Android build is unaffected.
            // Do not merge this branch to dev until the Xcode side is done. See docs/ci-cd.md / plan notes.
            implementation(libs.sentry.kotlin.multiplatform)
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)
        }
    }
}

