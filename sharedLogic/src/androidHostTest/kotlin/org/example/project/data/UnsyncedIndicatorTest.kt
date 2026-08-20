package org.example.project.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.model.ItemResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Arkivet skal vise om en kontroll ennå ikke har nådd skyen. Uten dette kan
 * mannskapet tro at et avvik er delt med de andre bilene når det bare ligger
 * lokalt på deres egen enhet.
 */
class UnsyncedIndicatorTest {

    private fun TestScenario.markAllSynced(runId: String) {
        db.checklistRunQueries.markRunSynced(runId)
        db.checklistResponseQueries.getResponsesForRun(runId).executeAsList().forEach {
            db.checklistResponseQueries.markResponseSynced(it.id)
        }
    }

    @Test
    fun `ny kontroll er merket som ikke synkronisert`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA)

        assertEquals(1L, s.repo.recentRuns().first().first().hasUnsyncedChanges)
    }

    @Test
    fun `merket som synkronisert når alt er sendt`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA)

        s.markAllSynced(runId)

        assertEquals(0L, s.repo.recentRuns().first().first().hasUnsyncedChanges)
    }

    @Test
    fun `usynket svar teller selv om kontrollen er sendt`() = runTest {
        val s = scenario()
        val first = s.addItem("Brannslukker")
        val second = s.addItem("Hjertestarter")
        val runId = s.startRun()
        s.repo.setResponse(runId, first, ItemResult.JA)
        s.markAllSynced(runId)

        // Nytt svar etter at kontrollen ble synket
        s.repo.setResponse(runId, second, ItemResult.NEI)

        assertEquals(
            1L, s.repo.recentRuns().first().first().hasUnsyncedChanges,
            "Et enkelt usynket svar er nok til å merke kontrollen",
        )
    }

    @Test
    fun `signering gjør kontrollen usynkronisert igjen`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA)
        s.markAllSynced(runId)

        s.repo.completeRun(runId, s.userId)

        assertEquals(
            1L, s.repo.recentRuns().first().first().hasUnsyncedChanges,
            "Signaturen må ut til de andre enhetene",
        )
    }
}
