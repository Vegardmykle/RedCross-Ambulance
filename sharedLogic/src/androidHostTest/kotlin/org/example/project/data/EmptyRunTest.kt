package org.example.project.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.model.ItemResult
import org.example.project.model.TemplateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * En kontroll som ble åpnet, men aldri besvart, er ikke dokumentasjon.
 * Slike skal ikke fylle opp arkivet mannskapet og driftsleder ser i.
 */
class EmptyRunTest {

    @Test
    fun `kontroll uten svar vises ikke i arkivet`() = runTest {
        val s = scenario()
        s.addItem("Brannslukker")

        s.startRun() // åpnet, men ingen svar gitt

        assertTrue(
            s.repo.recentRuns().first().isEmpty(),
            "Å åpne en sjekkliste er ikke en hendelse",
        )
    }

    @Test
    fun `kontroll dukker opp så snart noe er besvart`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()

        s.repo.setResponse(runId, item, ItemResult.JA)

        val runs = s.repo.recentRuns().first()
        assertEquals(1, runs.size)
        assertEquals(runId, runs.first().id)
    }

    @Test
    fun `påbegynt kontroll med svar bevares som utløpt`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.NEI, comment = "Mangler")

        // Simulerer at kontrollen ble påbegynt i går
        s.db.checklistRunQueries.applyRemoteRun(
            runId, s.templateId, s.ambulanceId, null,
            /* createdAt = */ 0L, null, "IN_PROGRESS", null, 1L,
        )
        s.startRun() // ny dag, ny kontroll

        val run = s.db.checklistRunQueries.getRunById(runId).executeAsOne()
        assertEquals("EXPIRED", run.status, "Svar som er gitt skal ikke forsvinne")
    }

    @Test
    fun `påbegynt kontroll uten svar ryddes bort`() = runTest {
        val s = scenario()
        s.addItem("Brannslukker")
        val runId = s.startRun()

        // Samme kontroll, men flyttet til i går – uten et eneste svar
        s.db.checklistRunQueries.applyRemoteRun(
            runId, s.templateId, s.ambulanceId, null,
            /* createdAt = */ 0L, null, "IN_PROGRESS", null, 1L,
        )
        val newRunId = s.startRun()

        assertTrue(newRunId != runId, "Det skal startes en ny kontroll for dagen")
        assertEquals(
            null, s.db.checklistRunQueries.getRunById(runId).executeAsOneOrNull(),
            "Den tomme kontrollen skal være slettet, ikke bevart som utløpt",
        )
    }

    @Test
    fun `liste uten punkter kan ikke signeres`() = runTest {
        val s = scenario()
        val tom = s.repo.createTemplate(name = "Tom liste", type = TemplateType.WEEKLY)
        val runId = s.repo.startOrResumeRun(tom, s.ambulanceId).id

        val error = assertFailsWith<IllegalStateException> {
            s.repo.completeRun(runId, s.userId)
        }
        assertTrue(
            error.message!!.contains("ingen punkter"),
            "Feilmeldingen bør forklare hvorfor, var: ${error.message}",
        )
    }
}
