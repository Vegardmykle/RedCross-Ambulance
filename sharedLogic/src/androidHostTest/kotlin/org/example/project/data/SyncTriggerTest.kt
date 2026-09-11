package org.example.project.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.model.ItemResult
import org.example.project.model.TemplateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hvem som utløser synkronisering.
 *
 * Utløseren lå tidligere i UI-et og ble tredd gjennom Compose-hierarkiet som
 * en callback. To skjermer på Android fikk den aldri, så maleditering og
 * administrasjon ble ikke sendt videre før neste appstart – mens iOS sendte
 * dem med én gang. Nå varsler repositoryet selv, og ingen skjerm kan glemme
 * det.
 */
class SyncTriggerTest {

    private class Recorder {
        var count = 0
        val trigger: () -> Unit = { count++ }
    }

    private suspend fun setup(): Pair<ChecklistRepository, Recorder> {
        val recorder = Recorder()
        val db = inMemoryDatabase()
        val repo = ChecklistRepository(db, recorder.trigger)
        repo.addAmbulance(callSign = "Ambulanse 1", registrationNumber = "EL12345")
        repo.addUser(id = "1234", name = "Kari Nordmann", role = "Ambulansearbeider")
        recorder.count = 0
        return repo to recorder
    }

    @Test
    fun `redigering av lista varsler synk`() = runTest {
        val (repo, rec) = setup()

        val templateId = repo.createTemplate("Sjekkliste før vakt", TemplateType.DAILY)
        assertEquals(1, rec.count, "Ny liste skal deles med de andre enhetene")

        repo.renameTemplate(templateId, "Daglig kontroll")
        assertEquals(2, rec.count)

        val itemId = repo.addItem(templateId, "Brannslukker")
        assertEquals(3, rec.count)

        repo.updateItem(itemId, "Brannslukker", description = "Bak førersetet")
        assertEquals(4, rec.count)

        repo.deleteItem(itemId)
        assertEquals(5, rec.count, "Også sletting må nå de andre bilene")
    }

    @Test
    fun `administrasjon varsler synk`() = runTest {
        val (repo, rec) = setup()

        repo.addUser("9999", "Ola Nordmann", "Mannskap")
        assertEquals(1, rec.count)

        val linkId = repo.addLink("Avviksmelding", "https://example.test")
        assertEquals(2, rec.count)

        repo.updateLink(linkId, "Avviksmelding", "https://example.test/ny")
        assertEquals(3, rec.count)

        repo.deleteLink(linkId)
        assertEquals(4, rec.count)
    }

    @Test
    fun `avkryssing av punkter varsler ikke synk`() = runTest {
        val (repo, rec) = setup()
        val templateId = repo.createTemplate("Sjekkliste", TemplateType.DAILY)
        val ambulanceId = repo.ambulancesForTest().first()
        val a = repo.addItem(templateId, "Brannslukker")
        val b = repo.addItem(templateId, "Hjertestarter")
        val runId = repo.startOrResumeRun(templateId, ambulanceId).id
        rec.count = 0

        repo.setResponse(runId, a, ItemResult.JA)
        repo.setResponse(runId, b, ItemResult.JA)

        assertEquals(
            0, rec.count,
            "Mannskapet krysser av hundrevis av punkter – de sendes samlet ved signering",
        )
    }

    @Test
    fun `signering varsler synk`() = runTest {
        val (repo, rec) = setup()
        val templateId = repo.createTemplate("Sjekkliste", TemplateType.DAILY)
        val ambulanceId = repo.ambulancesForTest().first()
        val item = repo.addItem(templateId, "Brannslukker")
        val runId = repo.startOrResumeRun(templateId, ambulanceId).id
        repo.setResponse(runId, item, ItemResult.JA)
        rec.count = 0

        repo.completeRun(runId, "1234")

        assertTrue(rec.count > 0, "Signaturen er dokumentasjon og må ut til de andre bilene")
    }
}

/** Første emisjon fra ambulanse-strømmen, som ID-er. */
private suspend fun ChecklistRepository.ambulancesForTest(): List<String> =
    ambulances().first().map { it.id }
