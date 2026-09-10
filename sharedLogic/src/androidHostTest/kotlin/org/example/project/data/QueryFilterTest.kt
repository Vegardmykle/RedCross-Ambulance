package org.example.project.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.model.ChecklistPhase
import org.example.project.model.ItemResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Filtre som avgjør hva mannskapet får se på dashbordet og i Mangler.
 *
 * Begge tilfellene under er blindveier: en påminnelse ingen kan kvittere ut,
 * og et avvik ingen kontroll kan lukke. Uten filtrene blir de stående for
 * alltid, og da mister listene troverdighet også for de ekte oppføringene.
 */
class QueryFilterTest {

    @Test
    fun `utlopt vakt star ikke igjen som ikke avsluttet`() = runTest {
        val s = scenario()
        val before = s.repo.addItem(s.templateId, "Brannslukker", phase = ChecklistPhase.BEFORE)
        s.repo.addItem(s.templateId, "Oksygen skrudd av", phase = ChecklistPhase.AFTER)

        val runId = s.startRun()
        s.repo.setResponse(runId, before, ItemResult.JA)
        s.repo.signBeforeShift(runId, s.userId)

        assertEquals(
            1, s.repo.runsAwaitingClosure().first().size,
            "Så lenge vakta pågår skal avslutningen etterlyses",
        )

        // Neste vakt starter uten at forrige ble avsluttet: den gamle utløper
        s.repo.startNewRun(s.templateId, s.ambulanceId)

        assertEquals(
            "EXPIRED", s.run(runId).status,
            "Svarene som ble gitt er dokumentasjon og skal bevares",
        )
        assertTrue(
            s.repo.runsAwaitingClosure().first().none { it.id == runId },
            "En utløpt vakt kan aldri avsluttes, så den skal ikke etterlyses",
        )
    }

    @Test
    fun `avvik pa slettet punkt skjules fra mangler`() = runTest {
        val s = scenario()
        val item = s.repo.addItem(s.templateId, "Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.ODELAGT, comment = "Trykket er borte")

        assertEquals(1, s.repo.openDeficiencies().first().size)

        s.repo.deleteItem(item)

        assertTrue(
            s.repo.openDeficiencies().first().isEmpty(),
            "Uten punktet finnes ingen kontroll som kan lukke avviket",
        )
    }
}
