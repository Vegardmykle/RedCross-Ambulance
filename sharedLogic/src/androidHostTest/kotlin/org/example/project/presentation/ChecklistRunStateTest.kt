package org.example.project.presentation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.data.TestScenario
import org.example.project.data.scenario
import org.example.project.model.ChecklistPhase
import org.example.project.model.ItemResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tilstanden sjekklisteskjermen viser.
 *
 * Denne logikken lå tidligere i Compose- og SwiftUI-koden og kunne ikke nås
 * av en test. Det er skjermen som avgjør om mannskapet får signere på at
 * bilen er kontrollert, så den fortjener dekning.
 */
class ChecklistRunStateTest {

    private suspend fun TestScenario.stateFor(runId: String): ChecklistRunState =
        checklistRunState(
            rootTemplateId = templateId,
            items = repo.itemsForTemplateTree(templateId).first(),
            responses = repo.responsesForRun(runId).first(),
            run = run(runId),
        )

    // ---------- Sekkepunkter (feilen som ga for lav teller) ----------

    @Test
    fun `sekkepunkter teller med selv om sekkene ikke vises`() = runTest {
        val s = scenario()
        val before = s.repo.addItem(s.templateId, "Brannslukker")
        val bag = s.addBag("Akuttkoffert")
        s.repo.addItem(bag, "Idealbind")
        s.repo.addItem(bag, "Kompress")

        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)

        val state = s.stateFor(runId)
        assertEquals(3, state.overall.total, "Sekkepunktene hører med i totalen")
        assertEquals(1, state.overall.answered)
        assertFalse(state.overall.isComplete)

        assertEquals(
            1, state.beforeItems.size,
            "Sekkepunktene vises i sine egne kort, ikke i hovedlista",
        )
    }

    @Test
    fun `vakta kan ikke avsluttes med ubesvart punkt i en sekk`() = runTest {
        val s = scenario()
        val before = s.repo.addItem(s.templateId, "Brannslukker", phase = ChecklistPhase.BEFORE)
        s.repo.addItem(s.templateId, "Oksygen skrudd av", phase = ChecklistPhase.AFTER)
        val bag = s.addBag("Akuttkoffert")
        val bagItem = s.repo.addItem(bag, "Idealbind")

        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.setResponse(runId, bagItem, ItemResult.JA)
        s.repo.signBeforeShift(runId, s.userId)

        // Etter signering skjules sekkene i UI-et. Tilstanden skal likevel
        // vite om dem – ellers blir «avslutt vakt» aktiv for tidlig.
        val afterItem = s.repo.itemsForTemplateTree(s.templateId).first()
            .first { ChecklistPhase.fromDb(it.phase) == ChecklistPhase.AFTER }

        val stillOpen = s.stateFor(runId)
        assertTrue(stillOpen.beforeSigned)
        assertEquals(3, stillOpen.overall.total)
        assertFalse(stillOpen.canComplete, "Avslutningspunktet er ikke besvart")

        s.repo.setResponse(runId, afterItem.id, ItemResult.JA)
        assertTrue(s.stateFor(runId).canComplete)
    }

    // ---------- Fasedeling ----------

    @Test
    fun `liste uten avslutningspunkter signeres i ett trinn`() = runTest {
        val s = scenario()
        val item = s.repo.addItem(s.templateId, "Full vask")

        val runId = s.startRun()
        assertFalse(s.stateFor(runId).canSignBefore, "Ubesvart punkt gir ingen signatur")

        s.repo.setResponse(runId, item, ItemResult.JA)

        val state = s.stateFor(runId)
        assertFalse(state.hasAfterPhase)
        assertTrue(state.canSignBefore, "Uten etter-punkter dekker knappen hele lista")
    }

    @Test
    fun `for-delen kan signeres uten at etter-punktene er besvart`() = runTest {
        val s = scenario()
        val before = s.repo.addItem(s.templateId, "Brannslukker", phase = ChecklistPhase.BEFORE)
        s.repo.addItem(s.templateId, "Vask av bil", phase = ChecklistPhase.AFTER)

        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)

        val state = s.stateFor(runId)
        assertTrue(state.hasAfterPhase)
        assertTrue(state.before.isComplete)
        assertTrue(state.canSignBefore)
        assertFalse(state.canComplete, "Vakta er ikke over før avslutningen er signert")
        assertEquals(1, state.afterItems.size)
        assertEquals(0, state.after.answered)
    }

    @Test
    fun `avslutningspunkter i sekker havner i etter-delen`() = runTest {
        val s = scenario()
        s.repo.addItem(s.templateId, "Brannslukker", phase = ChecklistPhase.BEFORE)
        val bag = s.addBag("Sidedør")
        s.repo.addItem(bag, "Oksygen skrudd av", phase = ChecklistPhase.AFTER)

        val state = s.stateFor(s.startRun())
        assertTrue(state.hasAfterPhase, "Et etter-punkt i en sekk deler også lista i to")
        assertEquals(1, state.afterItems.size)
    }

    // ---------- Tom liste ----------

    @Test
    fun `tom liste er ikke fullfort`() = runTest {
        val s = scenario()
        val state = s.stateFor(s.startRun())

        assertTrue(state.overall.isEmpty)
        assertFalse(
            state.canSignBefore,
            "En signatur på en tom liste ville sett ut som en gjennomført kontroll",
        )
    }

    // ---------- Avvikskvittering ----------

    @Test
    fun `signeringskvitteringen viser bare delen som signeres`() = runTest {
        val s = scenario()
        val before = s.repo.addItem(s.templateId, "Brannslukker", phase = ChecklistPhase.BEFORE)
        val after = s.repo.addItem(s.templateId, "Vask av bil", phase = ChecklistPhase.AFTER)

        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.ODELAGT, comment = "Trykket er borte")
        s.repo.setResponse(runId, after, ItemResult.NEI)

        val state = s.stateFor(runId)
        assertEquals(1, state.beforeDeficiencies.size)
        assertEquals("Brannslukker", state.beforeDeficiencies.first().title)
        assertEquals("Ødelagt", state.beforeDeficiencies.first().resultLabel)
        assertEquals("Trykket er borte", state.beforeDeficiencies.first().comment)

        assertEquals(2, state.allDeficiencies.size, "Ved avslutning meldes begge delene")
    }

    @Test
    fun `ubesvarte punkter er ikke avvik`() = runTest {
        val s = scenario()
        s.repo.addItem(s.templateId, "Brannslukker")

        assertTrue(
            s.stateFor(s.startRun()).allDeficiencies.isEmpty(),
            "Et punkt som ikke er kontrollert ennå er ikke meldt i uorden",
        )
    }

    // ---------- Rekkefølge ----------

    @Test
    fun `punktene star i den rekkefolgen utstyret ligger i bilen`() = runTest {
        val s = scenario()
        s.repo.addItem(s.templateId, "Først")
        s.repo.addItem(s.templateId, "Deretter")
        s.repo.addItem(s.templateId, "Til slutt")

        val runId = s.startRun()
        // Svar i motsatt rekkefølge – rekkefølgen skal ikke endre seg
        val items = s.repo.itemsForTemplateTree(s.templateId).first()
        s.repo.setResponse(runId, items.last().id, ItemResult.JA)

        assertEquals(
            listOf("Først", "Deretter", "Til slutt"),
            s.stateFor(runId).beforeItems.map { it.title },
            "Besvarte punkter skal ikke flytte seg under fingeren på mannskapet",
        )
    }
}
