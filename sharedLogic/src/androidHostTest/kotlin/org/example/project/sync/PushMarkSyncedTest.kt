package org.example.project.sync

import kotlinx.coroutines.test.runTest
import org.example.project.data.scenario
import org.example.project.model.ItemResult
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * At en rad bare merkes synket når det som ble sendt fortsatt er gjeldende.
 *
 * Push leser de usynkede radene, skriver dem til Firestore én og én og
 * merker dem synket. Endres en rad mellom lesingen og merkingen – mannskapet
 * svarer videre mens synken etter signering pågår – er det den gamle
 * versjonen som ble sendt. Merkes raden likevel synket, når endringen aldri
 * de andre bilene.
 *
 * Testene gjør det push gjør, men med en endring lagt inn midt i.
 */
class PushMarkSyncedTest {

    @Test
    fun `svar endret under push forblir usynket`() = runTest {
        val s = scenario()
        val item = s.addItem("Hansker")
        val run = s.startRun()
        s.repo.setResponse(run, item, ItemResult.JA)

        val sent = s.db.checklistResponseQueries.getUnsyncedResponses().executeAsList().single()
        // Endres mens skrivingen til Firestore pågår
        s.db.checklistResponseQueries.upsertResponse(
            sent.id, run, item, ItemResult.NEI.db, "mangler", null, sent.updatedAt + 1,
        )
        s.db.checklistResponseQueries.markResponseSynced(sent.id, sent.updatedAt)

        val now = s.responseFor(run, item)
        assertEquals(ItemResult.NEI.db, now.result)
        assertEquals(0L, now.synced, "Endringen er ikke sendt og må med i neste push")
    }

    @Test
    fun `uendret svar merkes synket`() = runTest {
        val s = scenario()
        val item = s.addItem("Hansker")
        val run = s.startRun()
        s.repo.setResponse(run, item, ItemResult.JA)

        val sent = s.db.checklistResponseQueries.getUnsyncedResponses().executeAsList().single()
        s.db.checklistResponseQueries.markResponseSynced(sent.id, sent.updatedAt)

        assertEquals(1L, s.responseFor(run, item).synced)
    }

    @Test
    fun `signering under push forblir usynket`() = runTest {
        val s = scenario()
        val item = s.addItem("Hansker")
        val run = s.startRun()
        s.repo.setResponse(run, item, ItemResult.JA)

        val sent = s.db.checklistRunQueries.getUnsyncedRuns().executeAsList().single()
        s.db.checklistRunQueries.completeRun(run, sent.updatedAt + 1, s.userId, null)
        s.db.checklistRunQueries.markRunSynced(sent.id, sent.updatedAt)

        assertEquals(0L, s.run(run).synced, "Signaturen må nå de andre bilene")
    }

    @Test
    fun `listeendring under push forblir usynket`() = runTest {
        val s = scenario()
        val sent = s.db.checklistTemplateQueries.getUnsyncedTemplates().executeAsList().single()
        s.db.checklistTemplateQueries.updateTemplateName(sent.id, "Nytt navn", sent.updatedAt + 1)
        s.db.checklistTemplateQueries.markTemplateSynced(sent.id, sent.updatedAt)

        val template = s.db.checklistTemplateQueries.getTemplateById(sent.id).executeAsOne()
        assertEquals(0L, template.synced)
    }
}
