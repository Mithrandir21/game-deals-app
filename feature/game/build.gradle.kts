plugins {
    alias(libs.plugins.gamedeals.kmp.feature)
    alias(libs.plugins.mokkery)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.zoomable)
            implementation(libs.vico.compose)
            implementation(libs.vico.compose.m3)
        }

    }
}

