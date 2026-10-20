package org.example.project.sync

import kotlinx.coroutines.test.runTest
import org.example.project.data.inMemoryDatabase
import org.example.project.data.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * At ett dokument som ikke lar seg legge inn ikke stopper resten av hentingen.
 *
 * Før ble hele hentingen avbrutt, og siden samme dokument kom tilbake hver
 * gang, fikk enheten aldri mer noe fra de andre bilene.
 */
class PullSkipTest {

    private fun run(id: String, status: String, templateId: String, ambulanceId: String) =
        RunDto(
            id = id, templateId = templateId, ambulanceId = ambulanceId,
            createdAt = 1000, status = status, updatedAt = 1000,
        )

    @Test
    fun `feil i ett dokument hopper bare over det ene`() {
        val service = FirebaseSyncService(inMemoryDatabase())
        val applied = mutableListOf<String>()

        val skipped = service.pullEach("runs", listOf("a", "b", "c"), { it }) { doc ->
            if (doc == "b") error("kan ikke leses")
            applied += doc
        }

        assertEquals(1, skipped)
        assertEquals(listOf("a", "c"), applied)
    }

    @Test
    fun `status fra en nyere appversjon stopper ikke de andre kjoringene`() = runTest {
        val s = scenario()
        val service = FirebaseSyncService(s.db)
        val docs = listOf(
            run("for", "COMPLETED", s.templateId, s.ambulanceId),
            // CHECK (status IN (...)) avviser en status denne versjonen ikke kjenner
            run("ukjent", "ARCHIVED", s.templateId, s.ambulanceId),
            run("etter", "IN_PROGRESS", s.templateId, s.ambulanceId),
        )

        val skipped = service.pullEach("runs", docs, { it.id }) { dto ->
            s.db.checklistRunQueries.applyRemoteRun(
                dto.id, dto.templateId, dto.ambulanceId, dto.userId, dto.createdAt,
                dto.completedAt, dto.status, dto.comment,
                dto.beforeSignedAt, dto.beforeUserId, dto.updatedAt,
            )
        }

        assertEquals(1, skipped)
        assertEquals("COMPLETED", s.run("for").status)
        assertEquals("IN_PROGRESS", s.run("etter").status)
        assertNull(s.db.checklistRunQueries.getRunById("ukjent").executeAsOneOrNull())
    }
}
