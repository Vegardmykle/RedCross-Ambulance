package org.example.project.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.model.ItemResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Avvikslivssyklusen: åpent → løst manuelt / løst ved ny kontroll (RECHECK)
 * / videreført til nytt avvik (SUPERSEDED). Dette er den mest sammensatte
 * logikken i appen, og den som er lettest å ødelegge ved endringer.
 */
class DeficiencyLifecycleTest {

    /** Signerer gjeldende kjøring og starter en ny, som en ny vakt ville gjort. */
    private suspend fun TestScenario.nextRun(currentRunId: String): String {
        repo.completeRun(currentRunId, userId)
        return startRun()
    }

    @Test
    fun `avvik dukker opp i oversikten over åpne mangler`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()

        s.repo.setResponse(runId, item, ItemResult.ODELAGT, comment = "Trykkmåler knust")

        val open = s.repo.openDeficiencies().first()
        assertEquals(1, open.size)
        assertEquals("Brannslukker", open.first().itemTitle)
        assertEquals("ODELAGT", open.first().result)
        assertEquals("Ambulanse 1", open.first().callSign)
    }

    @Test
    fun `ja-svar er ikke et avvik`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()

        s.repo.setResponse(runId, item, ItemResult.JA)

        assertTrue(s.repo.openDeficiencies().first().isEmpty())
    }

    @Test
    fun `ja ved neste kontroll lukker avviket automatisk som RECHECK`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")

        val firstRun = s.startRun()
        s.repo.setResponse(firstRun, item, ItemResult.NEI, comment = "Mangler")
        val secondRun = s.nextRun(firstRun)

        s.repo.setResponse(secondRun, item, ItemResult.JA)

        val old = s.responseFor(firstRun, item)
        assertEquals(1L, old.resolved)
        assertEquals("RECHECK", old.resolvedVia)
        assertEquals(secondRun, old.resolvedByRunId)
        assertNotNull(old.resolvedAt)
        assertTrue(s.repo.openDeficiencies().first().isEmpty())
    }

    @Test
    fun `nytt avvik på samme punkt viderefører det gamle`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")

        val firstRun = s.startRun()
        s.repo.setResponse(firstRun, item, ItemResult.NEI, comment = "Mangler")
        val secondRun = s.nextRun(firstRun)

        s.repo.setResponse(secondRun, item, ItemResult.ODELAGT, comment = "Fortsatt ikke fikset")

        val old = s.responseFor(firstRun, item)
        assertEquals(1L, old.resolved)
        assertEquals("SUPERSEDED", old.resolvedVia)

        // Bare det nyeste avviket vises som åpent – ikke to like
        val open = s.repo.openDeficiencies().first()
        assertEquals(1, open.size)
        assertEquals("ODELAGT", open.first().result)
    }

    /** Oversikten skal vise når problemet først ble meldt, ikke bare siste gang. */
    @Test
    fun `videreført avvik peker tilbake til første melding`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")

        val firstRun = s.startRun()
        s.repo.setResponse(firstRun, item, ItemResult.NEI)
        val firstReportedAt = s.responseFor(firstRun, item).checkedAt

        val secondRun = s.nextRun(firstRun)
        s.repo.setResponse(secondRun, item, ItemResult.NEI)
        val thirdRun = s.nextRun(secondRun)
        s.repo.setResponse(thirdRun, item, ItemResult.NEI)

        val open = s.repo.openDeficiencies().first()
        assertEquals(1, open.size)
        assertEquals(
            firstReportedAt, open.first().firstReportedAt,
            "Kjeden må følges helt tilbake til første kontroll",
        )
        assertEquals(s.userName, open.first().firstReportedByName)
    }

    @Test
    fun `ferskt avvik har ingen tidligere melding`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()

        s.repo.setResponse(runId, item, ItemResult.NEI)

        assertNull(s.repo.openDeficiencies().first().first().firstReportedAt)
    }

    // ---------- Angring når mannskapet bytter svar i samme kontroll ----------

    @Test
    fun `bytte fra ja til nei gjør om RECHECK til SUPERSEDED`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")

        val firstRun = s.startRun()
        s.repo.setResponse(firstRun, item, ItemResult.NEI)
        val secondRun = s.nextRun(firstRun)

        s.repo.setResponse(secondRun, item, ItemResult.JA)
        assertEquals("RECHECK", s.responseFor(firstRun, item).resolvedVia)

        // Mannskapet ombestemmer seg i samme kontroll
        s.repo.setResponse(secondRun, item, ItemResult.NEI)

        val old = s.responseFor(firstRun, item)
        assertEquals("SUPERSEDED", old.resolvedVia, "Feil lukking må ikke bli stående")
        assertEquals(1L, old.resolved)
        assertEquals(1, s.repo.openDeficiencies().first().size)
    }

    @Test
    fun `bytte fra nei til ja lukker det gamle avviket riktig`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")

        val firstRun = s.startRun()
        s.repo.setResponse(firstRun, item, ItemResult.NEI)
        val secondRun = s.nextRun(firstRun)

        s.repo.setResponse(secondRun, item, ItemResult.ODELAGT)
        assertEquals("SUPERSEDED", s.responseFor(firstRun, item).resolvedVia)

        s.repo.setResponse(secondRun, item, ItemResult.JA)

        assertEquals("RECHECK", s.responseFor(firstRun, item).resolvedVia)
        assertTrue(
            s.repo.openDeficiencies().first().isEmpty(),
            "Ingen avvik skal være åpne når punktet er meldt i orden",
        )
    }

    /** Fram og tilbake flere ganger skal ende i samme tilstand som én gang. */
    @Test
    fun `gjentatt bytting gir ingen dobbeltføring`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")

        val firstRun = s.startRun()
        s.repo.setResponse(firstRun, item, ItemResult.NEI)
        val secondRun = s.nextRun(firstRun)

        repeat(3) {
            s.repo.setResponse(secondRun, item, ItemResult.JA)
            s.repo.setResponse(secondRun, item, ItemResult.NEI)
        }

        assertEquals(1, s.repo.openDeficiencies().first().size)
        assertEquals(
            1, s.db.checklistResponseQueries.getResponsesForRun(secondRun).executeAsList().size,
            "Det skal bare finnes ett svar per punkt per kontroll",
        )
    }

    // ---------- Avgrensninger ----------

    /** En annen ambulanses avvik skal ikke lukkes av vår kontroll. */
    @Test
    fun `kontroll på én ambulanse lukker ikke avvik på en annen`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val otherAmbulance = s.repo.addAmbulance("Ambulanse 2", "EL99999")

        val otherRun = s.repo.startOrResumeRun(s.templateId, otherAmbulance).id
        s.repo.setResponse(otherRun, item, ItemResult.ODELAGT)

        val ourRun = s.startRun()
        s.repo.setResponse(ourRun, item, ItemResult.JA)

        assertEquals(
            0L, s.responseFor(otherRun, item).resolved,
            "Avvik på Ambulanse 2 må stå åpent til den selv kontrolleres",
        )
        assertEquals(1, s.repo.openDeficiencies().first().size)
    }

    @Test
    fun `manuelt løst avvik åpnes ikke igjen av senere kontroll`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")

        val firstRun = s.startRun()
        s.repo.setResponse(firstRun, item, ItemResult.NEI)
        s.repo.resolveDeficiency(s.responseFor(firstRun, item).id, s.userId)

        val secondRun = s.nextRun(firstRun)
        s.repo.setResponse(secondRun, item, ItemResult.NEI)

        val old = s.responseFor(firstRun, item)
        assertEquals("MANUAL", old.resolvedVia, "Manuell løsing skal ikke overskrives")
        assertEquals(s.userId, old.resolvedByUserId)
    }

    @Test
    fun `punkter med åpne avvik varsles i ny kontroll`() = runTest {
        val s = scenario()
        val withDeficiency = s.addItem("Brannslukker")
        val fine = s.addItem("Hjertestarter")

        val firstRun = s.startRun()
        s.repo.setResponse(firstRun, withDeficiency, ItemResult.NEI)
        s.repo.setResponse(firstRun, fine, ItemResult.JA)
        val secondRun = s.nextRun(firstRun)

        val flagged = s.repo
            .itemIdsWithOpenDeficiencies(s.ambulanceId, excludeRunId = secondRun)
            .first()

        assertEquals(listOf(withDeficiency), flagged)
    }
}
