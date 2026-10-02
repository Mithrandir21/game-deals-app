@file:OptIn(ExperimentalForeignApi::class)

package pm.bam.gamedeals.domain.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import platform.posix.getenv
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainMigrationsTest {

    private val databasePath = Path(SystemTemporaryDirectory, "migration-test-${Random.nextLong()}.db")

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = requireNotNull(getenv("ROOM_SCHEMA_DIR")).toKString(),
        fileName = databasePath.toString(),
        driver = BundledSQLiteDriver(),
        databaseClass = DomainDatabase::class,
        databaseFactory = { DomainDatabaseConstructor.initialize() },
    )

    @AfterTest
    fun tearDown() {
        helper.finished()
        SystemFileSystem.delete(databasePath, mustExist = false)
    }

    @Test
    fun migration_2_3_keeps_releases_and_expires_them_with_isMature_off() {
        helper.createDatabase(2).use { connection ->
            connection.execSQL("INSERT INTO `Release` (`title`, `date`, `image`, `expires`) VALUES ('Some Game', 1700000000, 'img', 9999999999999)")
        }

        helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).use { connection ->
            assertEquals(listOf(Triple("Some Game", 0L, 0L)), connection.releases())
        }
    }

    @Test
    fun every_registered_migration_reaches_the_current_schema() {
        helper.createDatabase(1).close()

        helper.runMigrationsAndValidate(DOMAIN_DB_VERSION, DOMAIN_MIGRATIONS.toList()).close()
    }

    private fun SQLiteConnection.releases(): List<Triple<String, Long, Long>> =
        prepare("SELECT `title`, `expires`, `isMature` FROM `Release`").use { statement ->
            buildList { while (statement.step()) add(Triple(statement.getText(0), statement.getLong(1), statement.getLong(2))) }
        }
}
