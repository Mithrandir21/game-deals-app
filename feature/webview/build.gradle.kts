import com.android.build.api.dsl.LibraryExtension

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

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.feature.webview"
}
