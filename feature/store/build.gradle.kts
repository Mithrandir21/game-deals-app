import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.gamedeals.kmp.feature)
    alias(libs.plugins.mokkery)
}

extensions.configure<LibraryExtension> {
    namespace = "pm.bam.gamedeals.feature.store"
}
