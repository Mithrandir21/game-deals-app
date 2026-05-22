package pm.bam.gamedeals

/**
 * Project-wide Kotlin compiler flags. Applied by [AndroidApplicationConventionPlugin] and [KotlinMultiplatformLibraryConventionPlugin] (which covers every
 * KMP library + feature/compose/ksp variant). The `iosApp` module bypasses both conventions and configures its own `compilerOptions { }` inline — keep its
 * list in sync with this one.
 */
internal val sharedFreeCompilerArgs = listOf(
    "-Xexplicit-backing-fields",
    "-Xreturn-value-checker=full",
)

/**
 * AGP `packaging.resources.excludes` shared by Android-application and KMP-library outputs. Drops META-INF noise that collides when Mokkery 3 + JUnit 4
 * pull in junit-jupiter (each ship `META-INF/LICENSE.md`) and works around an OSGi descriptor packaged inside org.jspecify:jspecify:1.0.0 +
 * com.squareup.okhttp3:logging-interceptor:5.2.1.
 */
internal val sharedPackagingExcludes = listOf(
    "META-INF/LICENSE.md",
    "META-INF/LICENSE-notice.md",
    "META-INF/{AL2.0,LGPL2.1}",
    "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
)
