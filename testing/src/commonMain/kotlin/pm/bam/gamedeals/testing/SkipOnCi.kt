package pm.bam.gamedeals.testing

/**
 * Tests/classes carrying this annotation are filtered out on CI via
 * `-Pandroid.testInstrumentationRunnerArguments.notAnnotation=pm.bam.gamedeals.testing.SkipOnCi`
 * in `.github/workflows/android.yml`. Use sparingly — currently scoped to tests
 * that drive the Espresso Device API, which cannot reach the emulator's gRPC
 * control service on the CI's externally-launched emulator.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class SkipOnCi
