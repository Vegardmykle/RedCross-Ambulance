package org.example.project.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.example.project.db.AppDatabase
import org.example.project.model.ChecklistPhase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migrasjon fra det skjemaet som faktisk står på enheten i bilen.
 *
 * Bakgrunn: 1.sqm la til kolonner med ALTER TABLE ADD COLUMN, som i SQLite
 * alltid plasserer dem sist. .sq-filene har dem midt i tabellen, så en
 * migrert database fikk en annen fysisk kolonnerekkefølge enn en fersk. Siden
 * SQLDelight leser posisjonelt (cursor.getString(9)), leste appen feil
 * kolonne på migrerte enheter – blant annet updatedAt der den ventet phase,
 * og NULL der den ventet en ikke-nullbar Long.
 *
 * Ingen oppdaget det, fordi alle installasjoner så langt hadde vært ferske.
 *
 * Gradle-oppgaven verifyCommonMainAppDatabaseMigration sammenligner skjemaer
 * og fanger rekkefølgen. Denne testen dekker det den ikke gjør: at appen
 * faktisk leser riktige verdier gjennom de genererte spørringene etterpå.
 */
class MigrationChainTest {

    /**
     * Skjemaet slik det sto i versjon 1.2, som er versjonen i drift:
     * uten phase, beforeSignedAt og beforeUserId.
     */
    private fun version1Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.exec(
            """
            CREATE TABLE ChecklistItem (
                id TEXT NOT NULL PRIMARY KEY,
                templateId TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                requiresValue INTEGER NOT NULL DEFAULT 0,
                unit TEXT,
                minValue REAL,
                maxValue REAL,
                sortOrder INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        driver.exec(
            """
            CREATE TABLE ChecklistRun (
                id TEXT NOT NULL PRIMARY KEY,
                templateId TEXT NOT NULL,
                ambulanceId TEXT NOT NULL,
                userId TEXT,
                createdAt INTEGER NOT NULL,
                completedAt INTEGER,
                status TEXT NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED','EXPIRED')),
                comment TEXT,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        return driver
    }

    private fun SqlDriver.exec(sql: String) = execute(null, sql, 0).value

    private fun SqlDriver.columnsOf(table: String): List<String> =
        executeQuery(
            null,
            "SELECT name FROM pragma_table_info('$table')",
            { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) names.add(cursor.getString(0)!!)
                QueryResult.Value(names.toList())
            },
            0,
        ).value

    @Test
    fun `migrert database far samme kolonnerekkefolge som fersk`() {
        val migrated = version1Database()
        AppDatabase.Schema.migrate(migrated, 1, AppDatabase.Schema.version).value

        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(fresh).value

        for (table in listOf("ChecklistItem", "ChecklistRun")) {
            assertEquals(
                fresh.columnsOf(table),
                migrated.columnsOf(table),
                "SQLDelight leser posisjonelt – avvikende rekkefølge gir feil kolonne i $table",
            )
        }
    }

    @Test
    fun `appen leser riktige verdier etter migrasjon`() {
        val driver = version1Database()

        // Data slik en enhet i drift ville hatt dem før oppdateringen
        driver.exec(
            """
            INSERT INTO ChecklistItem(id, templateId, title, requiresValue, sortOrder,
                                      updatedAt, deleted, synced)
            VALUES ('i1', 't1', 'Brannslukker', 0, 1, 1700000000000, 0, 1)
            """.trimIndent(),
        )
        driver.exec(
            """
            INSERT INTO ChecklistRun(id, templateId, ambulanceId, userId, createdAt,
                                     completedAt, status, updatedAt, synced)
            VALUES ('r1', 't1', 'a1', '1234', 1700000000000, 1700000100000,
                    'COMPLETED', 1700000100000, 1)
            """.trimIndent(),
        )

        AppDatabase.Schema.migrate(driver, 1, AppDatabase.Schema.version).value
        val db = AppDatabase(driver)

        val item = db.checklistItemQueries.getItemById("i1").executeAsOne()
        assertEquals("Brannslukker", item.title)
        assertEquals(
            ChecklistPhase.BEFORE.db, item.phase,
            "Punkter fra før to-fase-endringen skal havne i før-fasen",
        )
        assertEquals(1700000000000L, item.updatedAt, "updatedAt må ikke leses fra en annen kolonne")
        assertEquals(0L, item.deleted, "Et synket punkt skal ikke tolkes som slettet")
        assertEquals(1L, item.synced)

        // Denne raden er grunnen til at feilen ville krasjet appen: updatedAt
        // ble lest fra beforeSignedAt, som er NULL for alle eldre kontroller,
        // og mapperen krever en ikke-nullbar Long.
        val run = db.checklistRunQueries.getRunById("r1").executeAsOne()
        assertEquals("COMPLETED", run.status)
        assertEquals(1700000100000L, run.updatedAt)
        assertEquals(null, run.beforeSignedAt, "En gammel kontroll har ingen før-signatur")
        assertEquals(null, run.beforeUserId)
    }
}
