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
            // PostHog KMP — product-analytics bridge (Analytics / PostHogAnalytics / configurePostHog). Same
            // app-link story as Sentry on iOS: iosApp links posthog-ios via SPM (see docs/posthog-ios-handoff.md).
            implementation(libs.posthog.kmp)
        }

        androidMain.dependencies {
            implementation(libs.androidx.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

