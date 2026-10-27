package app.resonance.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the schema against unplanned changes. Every version bump must add a migration to
 * [ResonanceDatabase.MIGRATIONS] and a `migrate(N -> N+1)` case here that opens a database
 * created from the committed `schemas/` JSON of the previous version.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getDatabasePath(DB)

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = file,
        driver = AndroidSQLiteDriver(),
        databaseClass = ResonanceDatabase::class
    )

    @Test
    fun exportedSchemaMatchesTheEntities() {
        // Creating from the exported JSON and then opening with Room fails if the entities and the
        // committed schema have drifted apart (Room validates every table on open).
        helper.createDatabase(ResonanceDatabase.VERSION).close()
        val db = Room.databaseBuilder(context, ResonanceDatabase::class.java, file.absolutePath)
            .addMigrations(*ResonanceDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        assertEquals(ResonanceDatabase.VERSION, db.openHelper.writableDatabase.version)
        db.close()
    }

    @Test
    fun everyVersionAfterTheFirstHasAMigration() {
        val covered = ResonanceDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        val expected = (1 until ResonanceDatabase.VERSION).map { it to it + 1 }.toSet()
        assertEquals(expected, covered)
    }

    private companion object {
        const val DB = "migration-test"
    }
}
