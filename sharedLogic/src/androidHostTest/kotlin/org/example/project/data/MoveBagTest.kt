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
 * Flytting av sekk/taske mellom sjekklister. Punktene skal følge med, og
 * historikken skal ikke røres – en gjennomført kontroll er dokumentasjon.
 */
class MoveBagTest {

    private suspend fun TestScenario.weeklyList(): String =
        repo.createTemplate(name = "Ukentlig sjekk", type = TemplateType.WEEKLY)

    @Test
    fun `sekk med innhold flyttes til annen liste`() = runTest {
        val s = scenario()
        val bagId = s.addBag("Akuttkoffert")
        s.addItem("Larynxmaske", templateId = bagId)
        s.addItem("Sug", templateId = bagId)
        val weekly = s.weeklyList()

        s.repo.moveBag(bagId, weekly)

        assertTrue(
            s.repo.bagsFor(s.templateId).first().none { it.id == bagId },
            "Sekken skal ikke lenger ligge under den daglige lista",
        )
        val moved = s.repo.bagsFor(weekly).first()
        assertEquals(1, moved.size)
        assertEquals("Akuttkoffert", moved.first().name)
        assertEquals(
            2, s.repo.itemsFor(bagId).first().size,
            "Punktene peker på sekken og skal følge med",
        )
    }

    @Test
    fun `flyttet sekk teller med i signeringskravet på ny liste`() = runTest {
        val s = scenario()
        s.addItem("Brannslukker")
        val bagId = s.addBag("Akuttkoffert")
        s.addItem("Larynxmaske", templateId = bagId)
        val weekly = s.weeklyList()
        s.repo.addItem(templateId = weekly, title = "Lading")

        s.repo.moveBag(bagId, weekly)

        // Ukeslista har nå 2 punkter: sitt eget + sekkens
        val runId = s.repo.startOrResumeRun(weekly, s.ambulanceId).id
        s.repo.setResponse(runId, s.repo.itemsFor(weekly).first().first().id, ItemResult.JA)

        assertFailsWith<IllegalStateException>("Sekkens punkt mangler fortsatt svar") {
            s.repo.completeRun(runId, s.userId)
        }
    }

    @Test
    fun `tidligere kontroller påvirkes ikke av flytting`() = runTest {
        val s = scenario()
        val mainItem = s.addItem("Brannslukker")
        val bagId = s.addBag("Akuttkoffert")
        val bagItem = s.addItem("Larynxmaske", templateId = bagId)

        val runId = s.startRun()
        s.repo.setResponse(runId, mainItem, ItemResult.JA)
        s.repo.setResponse(runId, bagItem, ItemResult.NEI, comment = "Mangler")
        s.repo.completeRun(runId, s.userId)

        s.repo.moveBag(bagId, s.weeklyList())

        val responses = s.repo.responsesWithItems(runId).first()
        assertEquals(2, responses.size, "Svarene i den signerte kontrollen skal ligge urørt")
        val open = s.repo.openDeficiencies().first()
        assertEquals(1, open.size)
        assertEquals("Larynxmaske", open.first().itemTitle)
    }

    // ---------- Avvisning av ulovlige flyttinger ----------

    @Test
    fun `sekk kan ikke bli sin egen hovedliste`() = runTest {
        val s = scenario()
        val bagId = s.addBag("Akuttkoffert")

        assertFailsWith<IllegalArgumentException> {
            s.repo.moveBag(bagId, bagId)
        }
    }

    @Test
    fun `sekk kan ikke legges inni en annen sekk`() = runTest {
        val s = scenario()
        val first = s.addBag("Akuttkoffert")
        val second = s.addBag("Oksygensekk")

        assertFailsWith<IllegalStateException> {
            s.repo.moveBag(first, second)
        }
    }

    @Test
    fun `hovedliste kan ikke flyttes`() = runTest {
        val s = scenario()
        val weekly = s.weeklyList()

        assertFailsWith<IllegalStateException>("Bare sekker kan flyttes") {
            s.repo.moveBag(s.templateId, weekly)
        }
    }

    @Test
    fun `kan ikke flyttes til slettet liste`() = runTest {
        val s = scenario()
        val bagId = s.addBag("Akuttkoffert")
        val weekly = s.weeklyList()
        s.repo.deleteTemplate(weekly)

        assertFailsWith<IllegalStateException> {
            s.repo.moveBag(bagId, weekly)
        }
    }

    @Test
    fun `flytting merkes for synkronisering`() = runTest {
        val s = scenario()
        val bagId = s.addBag("Akuttkoffert")
        val weekly = s.weeklyList()
        s.db.checklistTemplateQueries.markTemplateSynced(bagId)

        s.repo.moveBag(bagId, weekly)

        val bag = s.db.checklistTemplateQueries.getTemplateById(bagId).executeAsOne()
        assertEquals(0L, bag.synced, "Endringen må nå de andre enhetene")
        assertEquals(weekly, bag.parentId)
    }
}
