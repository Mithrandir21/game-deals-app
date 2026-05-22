package pm.bam.gamedeals.domain.db

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainDatabaseMigrationTest {

    @Test
    fun currentVersionHasACheckedInSchemaFile() {
        val schema = File(schemaDir(), "$DOMAIN_DB_VERSION.json")
        assertTrue(
            "Missing $schema. Run `./gradlew :domain:kspAndroidMain` and commit the generated file.",
            schema.exists(),
        )
    }

    @Test
    fun everyVersionTransitionHasAMigration() {
        if (DOMAIN_DB_VERSION <= FIRST_EXPORTED_VERSION) return

        val registered = DOMAIN_MIGRATIONS
            .map { it.startVersion to it.endVersion }
            .toSet()

        for (from in FIRST_EXPORTED_VERSION until DOMAIN_DB_VERSION) {
            val transition = from to from + 1
            assertTrue(
                "DomainDatabase is at v$DOMAIN_DB_VERSION but no migration from v$from to v${from + 1} " +
                    "is registered. Add a Migration($from, ${from + 1}) to DOMAIN_MIGRATIONS, " +
                    "or accept data loss by extending fallbackToDestructiveMigrationFrom in DomainModule.",
                transition in registered,
            )
        }
    }

    private fun schemaDir(): File =
        File(System.getProperty("user.dir"), "schemas/${DomainDatabase::class.java.canonicalName}")

    private companion object {
        const val FIRST_EXPORTED_VERSION = 5
    }
}
