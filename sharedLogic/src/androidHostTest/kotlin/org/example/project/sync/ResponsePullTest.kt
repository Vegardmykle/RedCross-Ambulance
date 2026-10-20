package org.example.project.sync

import database.ChecklistResponse
import kotlinx.coroutines.test.runTest
import org.example.project.data.TestScenario
import org.example.project.data.scenario
import org.example.project.model.ItemResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Svar som hentes fra skyen, når det finnes flere dokumenter for samme punkt.
 *
 * Eldre appversjoner ga et svar ny id hver gang det ble endret. Det gamle
 * dokumentet ble liggende i Firestore, og en enhet som hentet det etter det
 * nye viste det gamle svaret – et punkt rettet fra NEI til JA kunne dukke opp
 * igjen som åpent avvik på en annen bil.
 */
class ResponsePullTest {

    private fun TestScenario.remote(
        id: String,
        runId: String,
        itemId: String,
        result: ItemResult,
        updatedAt: Long,
    ) = ResponseDto(
        id = id,
        checklistRunId = runId,
        itemId = itemId,
        result = result.db,
        checkedAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun TestScenario.responses(runId: String): List<ChecklistResponse> =
        db.checklistResponseQueries.getResponsesForRun(runId).executeAsList()

    @Test
    fun `endret svar beholder id-en sin`() = runTest {
        val s = scenario()
        val item = s.addItem("Hansker")
        val run = s.startRun()

        s.repo.setResponse(run, item, ItemResult.NEI, "mangler")
        val first = s.responseFor(run, item).id
        s.repo.setResponse(run, item, ItemResult.JA)

        assertEquals(first, s.responseFor(run, item).id)
        assertEquals(ItemResult.JA.db, s.responseFor(run, item).result)
    }

    @Test
    fun `eldre dokument med annen id overskriver ikke nyere svar`() = runTest {
        val s = scenario()
        val item = s.addItem("Hansker")
        val run = s.startRun()
        val sync = FirebaseSyncService(s.db)

        sync.applyRemoteResponse(s.remote("ny", run, item, ItemResult.JA, updatedAt = 2000))
        sync.applyRemoteResponse(s.remote("gammel", run, item, ItemResult.NEI, updatedAt = 1000))

        val only = s.responses(run).single()
        assertEquals("ny", only.id)
        assertEquals(ItemResult.JA.db, only.result)
    }

    @Test
    fun `nyere dokument med annen id erstatter svaret`() = runTest {
        val s = scenario()
        val item = s.addItem("Hansker")
        val run = s.startRun()
        val sync = FirebaseSyncService(s.db)

        sync.applyRemoteResponse(s.remote("gammel", run, item, ItemResult.NEI, updatedAt = 1000))
        sync.applyRemoteResponse(s.remote("ny", run, item, ItemResult.JA, updatedAt = 2000))

        val only = s.responses(run).single()
        assertEquals("ny", only.id)
        assertEquals(ItemResult.JA.db, only.result)
    }

    @Test
    fun `rekkefolgen dokumentene hentes i avgjor ikke utfallet`() = runTest {
        val old = { s: TestScenario, run: String, item: String ->
            s.remote("gammel", run, item, ItemResult.NEI, updatedAt = 1000)
        }
        val new = { s: TestScenario, run: String, item: String ->
            s.remote("ny", run, item, ItemResult.JA, updatedAt = 2000)
        }

        for (newFirst in listOf(true, false)) {
            val s = scenario()
            val item = s.addItem("Hansker")
            val run = s.startRun()
            val sync = FirebaseSyncService(s.db)
            val order = if (newFirst) listOf(new, old) else listOf(old, new)

            // To runder, som når hentevinduene overlapper
            repeat(2) { order.forEach { sync.applyRemoteResponse(it(s, run, item)) } }

            assertEquals(ItemResult.JA.db, s.responses(run).single().result, "newFirst=$newFirst")
        }
    }

    @Test
    fun `likt tidsstempel gir samme vinner pa alle enheter`() = runTest {
        for (aFirst in listOf(true, false)) {
            val s = scenario()
            val item = s.addItem("Hansker")
            val run = s.startRun()
            val sync = FirebaseSyncService(s.db)
            val a = s.remote("a", run, item, ItemResult.NEI, updatedAt = 1000)
            val b = s.remote("b", run, item, ItemResult.JA, updatedAt = 1000)

            (if (aFirst) listOf(a, b) else listOf(b, a)).forEach(sync::applyRemoteResponse)

            assertEquals("b", s.responses(run).single().id, "aFirst=$aFirst")
        }
    }

    @Test
    fun `usynket lokalt svar som er nyere blir staende`() = runTest {
        val s = scenario()
        val item = s.addItem("Hansker")
        val run = s.startRun()
        s.repo.setResponse(run, item, ItemResult.JA)
        val local = s.responseFor(run, item)

        FirebaseSyncService(s.db).applyRemoteResponse(
            s.remote("fra-skyen", run, item, ItemResult.ODELAGT, updatedAt = local.updatedAt - 1),
        )

        assertEquals(local.id, s.responseFor(run, item).id)
        assertEquals(0L, s.responseFor(run, item).synced, "må fortsatt sendes")
    }
}
