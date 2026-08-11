package org.example.project.data

import kotlinx.coroutines.test.runTest
import org.example.project.model.ItemResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Signering er siste skanse før en sjekkliste regnes som godkjent.
 * Disse testene sikrer at en liste ikke kan lukkes uten mannskaps-ID
 * og uten at alle punkter faktisk er besvart.
 */
class CompleteRunTest {

    @Test
    fun `krever mannskaps-ID`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA)

        assertFailsWith<IllegalArgumentException> {
            s.repo.completeRun(runId, userId = "")
        }
        assertFailsWith<IllegalArgumentException> {
            s.repo.completeRun(runId, userId = "   ")
        }
        assertEquals("IN_PROGRESS", s.run(runId).status)
    }

    @Test
    fun `kan ikke signeres med ubesvarte punkter`() = runTest {
        val s = scenario()
        val first = s.addItem("Brannslukker")
        s.addItem("Hjertestarter")
        val runId = s.startRun()
        s.repo.setResponse(runId, first, ItemResult.JA)

        val error = assertFailsWith<IllegalStateException> {
            s.repo.completeRun(runId, s.userId)
        }
        assertTrue(
            error.message!!.contains("1 av 2"),
            "Feilmeldingen bør si hvor mange som mangler, var: ${error.message}",
        )
        assertEquals("IN_PROGRESS", s.run(runId).status)
    }

    /** Punkter inne i sekker/tasker teller også – de er lett å glemme. */
    @Test
    fun `punkter i sekker teller med`() = runTest {
        val s = scenario()
        val mainItem = s.addItem("Brannslukker")
        val bagId = s.addBag("Akuttkoffert")
        val bagItem = s.addItem("Larynxmaske", templateId = bagId)
        val runId = s.startRun()

        s.repo.setResponse(runId, mainItem, ItemResult.JA)
        assertFailsWith<IllegalStateException>("Sekkepunktet mangler – skal ikke kunne signeres") {
            s.repo.completeRun(runId, s.userId)
        }

        s.repo.setResponse(runId, bagItem, ItemResult.JA)
        s.repo.completeRun(runId, s.userId)
        assertEquals("COMPLETED", s.run(runId).status)
    }

    /** Slettede punkter skal ikke blokkere signering. */
    @Test
    fun `slettet punkt blokkerer ikke signering`() = runTest {
        val s = scenario()
        val keep = s.addItem("Brannslukker")
        val removed = s.addItem("Utgått utstyr")
        val runId = s.startRun()

        s.repo.setResponse(runId, keep, ItemResult.JA)
        s.repo.deleteItem(removed)

        s.repo.completeRun(runId, s.userId)
        assertEquals("COMPLETED", s.run(runId).status)
    }

    @Test
    fun `vellykket signering lagrer mannskap, tidspunkt og kommentar`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA)

        assertNull(s.run(runId).completedAt)
        s.repo.completeRun(runId, s.userId, comment = "Alt i orden")

        val run = s.run(runId)
        assertEquals("COMPLETED", run.status)
        assertEquals(s.userId, run.userId)
        assertEquals("Alt i orden", run.comment)
        assertNotNull(run.completedAt)
        assertEquals(0L, run.synced, "Signert kjøring må merkes for synkronisering")
    }

    @Test
    fun `kan ikke signeres to ganger`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA)
        s.repo.completeRun(runId, s.userId)

        assertFailsWith<IllegalStateException> {
            s.repo.completeRun(runId, userId = "9999")
        }
        assertEquals(s.userId, s.run(runId).userId, "Første signatur skal ikke overskrives")
    }

    @Test
    fun `svar kan ikke endres etter signering`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA)
        s.repo.completeRun(runId, s.userId)

        assertFailsWith<IllegalStateException> {
            s.repo.setResponse(runId, item, ItemResult.ODELAGT)
        }
        assertEquals("JA", s.responseFor(runId, item).result)
    }

    /** En liste med avvik skal fortsatt kunne signeres – avviket følges opp etterpå. */
    @Test
    fun `liste med avvik kan signeres`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.ODELAGT, comment = "Trykkmåler knust")

        s.repo.completeRun(runId, s.userId)
        assertEquals("COMPLETED", s.run(runId).status)
    }
}
