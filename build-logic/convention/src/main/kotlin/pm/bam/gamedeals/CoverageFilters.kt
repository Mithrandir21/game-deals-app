package pm.bam.gamedeals

/**
 * Canonical code-coverage exclusion set. Single source of truth for both Kover (host tests, FQN-glob filters) and JaCoCo (instrumented device tests,
 * path-glob filters on `.class` files).
 *
 * Consumed by [configureKover] (per-module + root-aggregated Kover) and by the root `jacocoAndroidTestReport` task. Both forms must reflect the same intent;
 * the JaCoCo grammar differs because it filters on file paths, not class names.
 */
object CoverageFilters {
    val excludedPackages = listOf(
        "*.di",
        "*.generated.resources",
    )

    val excludedClassGlobs = listOf(
        "*BuildConfig",
        "*ComposableSingletons*",
        "*\$\$serializer",
    )

    val excludedAnnotations = listOf(
        "*Generated*",
    )

    val jacocoPathGlobs = listOf(
        "**/di/**",
        "**/generated/resources/**",
        "**/*BuildConfig*",
        "**/*ComposableSingletons*",
        "**/*\$\$serializer*",
        "**/R.class",
        "**/R\$*.class",
        "**/Manifest*.*",
    )
}
