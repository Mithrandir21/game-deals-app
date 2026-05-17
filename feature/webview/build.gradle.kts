plugins {
    alias(libs.plugins.gamedeals.kmp.feature)
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.webview)
        }
    }
}

