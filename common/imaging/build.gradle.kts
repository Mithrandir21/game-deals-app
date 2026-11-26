plugins {
    alias(libs.plugins.gamedeals.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Coil core (coil3.util.Logger) + Ktor network fetcher (coil3.network.HttpException via
            // coil-network-core). okio.IOException rides in transitively on Coil's api.
            implementation(libs.coil3)
            implementation(libs.coil3.network.ktor)
            // App logging seam — the Coil->Logger bridge maps Coil log events onto our LogLevel.
            implementation(project(":logging"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
