package org.example.project.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.model.ChecklistPhase
import org.example.project.model.ItemResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vakta signeres i to trinn: før-kontroll ved oppstart, avslutning når vakta
 * er ferdig. Bakgrunnen er at avslutningspunktene lå blandet inn i
 * før-lista, og mannskapet trodde de måtte begynne på nytt etter vakta.
 */
class TwoPhaseShiftTest {

    private suspend fun TestScenario.beforeItem(title: String) =
        repo.addItem(templateId, title, phase = ChecklistPhase.BEFORE)

    private suspend fun TestScenario.afterItem(title: String) =
        repo.addItem(templateId, title, phase = ChecklistPhase.AFTER)

    // ---------- Signering av før-delen ----------

    @Test
    fun `for-delen kan signeres uten at etter-punktene er rort`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        s.afterItem("Oksygen skrudd av")
        val runId = s.startRun()

        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.signBeforeShift(runId, s.userId)

        val run = s.run(runId)
        assertNotNull(run.beforeSignedAt, "Før-delen skal være signert")
        assertEquals(s.userId, run.beforeUserId)
        assertEquals("IN_PROGRESS", run.status, "Vakta er ikke over før avslutningen er signert")
    }

    @Test
    fun `for-delen kan ikke signeres med ubesvarte for-punkter`() = runTest {
        val s = scenario()
        s.beforeItem("Brannslukker")
        s.beforeItem("Hjertestarter")
        val runId = s.startRun()

        assertFailsWith<IllegalStateException> {
            s.repo.signBeforeShift(runId, s.userId)
        }
    }

    @Test
    fun `for-delen krever kjent mannskaps-ID`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)

        assertFailsWith<IllegalArgumentException> {
            s.repo.signBeforeShift(runId, "9999")
        }
    }

    @Test
    fun `for-delen kan ikke signeres to ganger`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.signBeforeShift(runId, s.userId)

        assertFailsWith<IllegalStateException> {
            s.repo.signBeforeShift(runId, s.userId)
        }
    }

    // ---------- Gjenåpning ----------

    @Test
    fun `for-delen kan gjenapnes mens vakta pagar`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.signBeforeShift(runId, s.userId)

        s.repo.reopenBeforeShift(runId)

        assertNull(s.run(runId).beforeSignedAt)
        // Og svarene kan endres igjen
        s.repo.setResponse(runId, before, ItemResult.NEI, comment = "Oppdaget mangel")
        assertEquals("NEI", s.responseFor(runId, before).result)
    }

    // ---------- Avslutning ----------

    @Test
    fun `vakta kan ikke avsluttes for for-delen er signert`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        val after = s.afterItem("Oksygen skrudd av")
        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.setResponse(runId, after, ItemResult.JA)

        val error = assertFailsWith<IllegalStateException> {
            s.repo.completeRun(runId, s.userId)
        }
        assertTrue(error.message!!.contains("Før-vakt-delen"))
    }

    @Test
    fun `avslutningen kan signeres av et annet mannskap`() = runTest {
        val s = scenario()
        s.repo.addUser("5678", "Ola Nordmann", "Ambulansearbeider")
        val before = s.beforeItem("Brannslukker")
        val after = s.afterItem("Oksygen skrudd av")
        val runId = s.startRun()

        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.signBeforeShift(runId, s.userId)
        s.repo.setResponse(runId, after, ItemResult.JA)
        s.repo.completeRun(runId, "5678")

        val run = s.run(runId)
        assertEquals("COMPLETED", run.status)
        assertEquals(s.userId, run.beforeUserId, "Begge signaturene skal bevares")
        assertEquals("5678", run.userId)
    }

    @Test
    fun `avslutningen krever at etter-punktene er besvart`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        s.afterItem("Oksygen skrudd av")
        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.signBeforeShift(runId, s.userId)

        assertFailsWith<IllegalStateException>("Etter-punktet mangler svar") {
            s.repo.completeRun(runId, s.userId)
        }
    }

    @Test
    fun `for-delen kan ikke gjenapnes etter at vakta er avsluttet`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        val after = s.afterItem("Oksygen skrudd av")
        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.signBeforeShift(runId, s.userId)
        s.repo.setResponse(runId, after, ItemResult.JA)
        s.repo.completeRun(runId, s.userId)

        assertFailsWith<IllegalStateException> {
            s.repo.reopenBeforeShift(runId)
        }
    }

    // ---------- Påminnelse om manglende avslutning ----------

    @Test
    fun `pabegynt vakt vises som ventende pa avslutning`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        s.afterItem("Oksygen skrudd av")
        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)

        assertTrue(
            s.repo.runsAwaitingClosure().first().isEmpty(),
            "Ikke før før-delen er signert",
        )

        s.repo.signBeforeShift(runId, s.userId)

        val awaiting = s.repo.runsAwaitingClosure().first()
        assertEquals(1, awaiting.size)
        assertEquals(s.userName, awaiting.first().beforeSignedByName)
    }

    @Test
    fun `avsluttet vakt vises ikke som ventende`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        val after = s.afterItem("Oksygen skrudd av")
        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.signBeforeShift(runId, s.userId)
        s.repo.setResponse(runId, after, ItemResult.JA)
        s.repo.completeRun(runId, s.userId)

        assertTrue(s.repo.runsAwaitingClosure().first().isEmpty())
    }

    // ---------- Ny vakt selv om forrige ikke ble avsluttet ----------

    @Test
    fun `ny vakt kan startes selv om forrige aldri ble avsluttet`() = runTest {
        val s = scenario()
        val before = s.beforeItem("Brannslukker")
        s.afterItem("Oksygen skrudd av")
        val first = s.startRun()
        s.repo.setResponse(first, before, ItemResult.NEI, comment = "Mangler")
        s.repo.signBeforeShift(first, s.userId)

        val second = s.repo.startNewRun(s.templateId, s.ambulanceId).id

        assertTrue(second != first, "Det skal være en ny kontroll")
        assertEquals(
            "EXPIRED", s.run(first).status,
            "Den gamle bevares – svarene er dokumentasjon på hva som ble sjekket",
        )
        assertEquals("IN_PROGRESS", s.run(second).status)
        // Avviket fra forrige vakt følger med som åpent
        assertEquals(1, s.repo.openDeficiencies().first().size)
    }

    @Test
    fun `ny vakt sletter forrige hvis ingenting var besvart`() = runTest {
        val s = scenario()
        s.beforeItem("Brannslukker")
        val first = s.startRun()

        val second = s.repo.startNewRun(s.templateId, s.ambulanceId).id

        assertNull(
            s.db.checklistRunQueries.getRunById(first).executeAsOneOrNull(),
            "En tom kontroll er ingen dokumentasjon",
        )
        assertEquals("IN_PROGRESS", s.run(second).status)
    }
}
