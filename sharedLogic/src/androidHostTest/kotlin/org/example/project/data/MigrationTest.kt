package org.example.project.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.example.project.db.AppDatabase
import org.example.project.model.ChecklistPhase
import org.example.project.model.ItemResult
import org.example.project.model.TemplateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * At migrasjonene faktisk fører fra ett skjema til det neste verifiseres av
 * Gradle, ikke her: `verifyMigrations = true` kjører `.sqm`-filene mot de
 * lagrede `.db`-øyeblikksbildene under bygget, og feiler hvis resultatet
 * avviker fra `.sq`-filene. Det er den eneste måten å teste det på – i en
 * enhetstest lager `Schema.create()` alltid nyeste skjema, så en migrasjon
 * derfra ville forsøkt å legge til kolonner som allerede finnes.
 *
 * Det disse testene dekker er det Gradle ikke ser: at skjemaet etter
 * migrasjon faktisk fungerer med spørringene og forretningslogikken. En
 * migrasjon kan gå gjennom og likevel etterlate noe repositoryet ikke
 * forstår.
 */
class MigrationTest {

    private fun freshDatabase(): AppDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return AppDatabase(driver)
    }

    @Test
    fun `skjemaversjonen holder folge med migrasjonsfilene`() {
        // Versjon = antall .sqm-filer + 1. Er den 1 etter at en migrasjon er
        // lagt til, er skjemaet ikke regenerert og enhetene vil ikke migrere.
        assertTrue(
            AppDatabase.Schema.version >= 2,
            "Forventet minst versjon 2 etter 1.sqm, var ${AppDatabase.Schema.version}",
        )
    }

    @Test
    fun `hele flyten fungerer pa nyeste skjema`() = runTest {
        val db = freshDatabase()
        val repo = ChecklistRepository(db)

        val ambulanceId = repo.addAmbulance("Ambulanse 1", "EL12345")
        repo.addUser("1234", "Kari Nordmann", "Ambulansearbeider")
        val templateId = repo.createTemplate("Sjekkliste før vakt", TemplateType.DAILY)
        val before = repo.addItem(templateId, "Brannslukker", phase = ChecklistPhase.BEFORE)
        val after = repo.addItem(templateId, "Oksygen skrudd av", phase = ChecklistPhase.AFTER)

        val runId = repo.startOrResumeRun(templateId, ambulanceId).id
        repo.setResponse(runId, before, ItemResult.JA)
        repo.signBeforeShift(runId, "1234")
        repo.setResponse(runId, after, ItemResult.JA)
        repo.completeRun(runId, "1234")

        val run = db.checklistRunQueries.getRunById(runId).executeAsOne()
        assertEquals("COMPLETED", run.status)
        assertEquals("1234", run.beforeUserId)
    }

    @Test
    fun `punkter fra for migrasjonen havner i for-fasen`() = runTest {
        val db = freshDatabase()
        val repo = ChecklistRepository(db)
        val templateId = repo.createTemplate("Liste", TemplateType.DAILY)

        // Simulerer en rad skrevet før phase-kolonnen fantes: migrasjonen gir
        // den DEFAULT 'BEFORE', slik at ingen punkter blir usynlige for
        // mannskapet etter oppdatering
        val itemId = repo.addItem(templateId, "Gammelt punkt")

        assertEquals(
            ChecklistPhase.BEFORE.db,
            db.checklistItemQueries.getItemById(itemId).executeAsOne().phase,
        )
    }
}
