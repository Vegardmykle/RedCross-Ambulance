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
 * Grenseverdier på målepunkter (f.eks. oksygentrykk 180–250 bar).
 * Her ligger den farligste feilmuligheten: at en verdi utenfor grensa
 * blir stående som «i orden».
 */
class MeasurementLimitsTest {

    private suspend fun oksygen(s: TestScenario) =
        s.addMeasurementItem("Oksygenflaske", unit = "bar", min = 180.0, max = 250.0)

    @Test
    fun `verdi under minimum flagges som mangelfull selv om mannskapet svarte ja`() = runTest {
        val s = scenario()
        val item = oksygen(s)
        val runId = s.startRun()

        s.repo.setResponse(runId, item, ItemResult.JA, reading = "150")

        val response = s.responseFor(runId, item)
        assertEquals("MANGELFULL", response.result)
        assertEquals("150", response.reading, "Den avleste verdien må bevares som den ble skrevet")
        assertNotNull(response.comment)
        assertTrue(
            response.comment!!.contains("180"),
            "Kommentaren bør oppgi grensa, var: ${response.comment}",
        )
    }

    @Test
    fun `verdi over maksimum flagges som mangelfull`() = runTest {
        val s = scenario()
        val item = oksygen(s)
        val runId = s.startRun()

        s.repo.setResponse(runId, item, ItemResult.JA, reading = "300")

        assertEquals("MANGELFULL", s.responseFor(runId, item).result)
    }

    /** Grensene er inklusive – nøyaktig 180 eller 250 bar er godkjent. */
    @Test
    fun `verdi nøyaktig på grensa er godkjent`() = runTest {
        val s = scenario()
        val item = oksygen(s)

        val firstRun = s.startRun()
        s.repo.setResponse(firstRun, item, ItemResult.JA, reading = "180")
        assertEquals("JA", s.responseFor(firstRun, item).result)
        s.repo.completeRun(firstRun, s.userId)

        val secondRun = s.startRun()
        s.repo.setResponse(secondRun, item, ItemResult.JA, reading = "250")
        assertEquals("JA", s.responseFor(secondRun, item).result)
    }

    @Test
    fun `bare minimum satt - ingen øvre grense`() = runTest {
        val s = scenario()
        val item = s.addMeasurementItem("Drivstoff", unit = "%", min = 50.0, max = null)
        val runId = s.startRun()

        s.repo.setResponse(runId, item, ItemResult.JA, reading = "99")
        assertEquals("JA", s.responseFor(runId, item).result)
    }

    @Test
    fun `desimalverdier håndteres`() = runTest {
        val s = scenario()
        val item = s.addMeasurementItem("Temperatur", unit = "°C", min = 2.0, max = 8.0)
        val runId = s.startRun()

        s.repo.setResponse(runId, item, ItemResult.JA, reading = "1.9")
        assertEquals("MANGELFULL", s.responseFor(runId, item).result)
    }

    @Test
    fun `ugyldig tall avvises`() = runTest {
        val s = scenario()
        val item = oksygen(s)
        val runId = s.startRun()

        assertFailsWith<IllegalArgumentException> {
            s.repo.setResponse(runId, item, ItemResult.JA, reading = "ca 200")
        }
    }

    /** Mannskapets egen kommentar skal ikke overskrives av den automatiske. */
    @Test
    fun `egen kommentar beholdes ved automatisk flagging`() = runTest {
        val s = scenario()
        val item = oksygen(s)
        val runId = s.startRun()

        s.repo.setResponse(
            runId, item, ItemResult.JA,
            comment = "Byttet flaske, ny er bestilt", reading = "150",
        )

        val response = s.responseFor(runId, item)
        assertEquals("MANGELFULL", response.result)
        assertEquals("Byttet flaske, ny er bestilt", response.comment)
    }

    // ---------- Løsing av avvik ----------

    @Test
    fun `løsing krever kjent mannskaps-ID`() = runTest {
        val s = scenario()
        val item = oksygen(s)
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA, reading = "150")
        val responseId = s.responseFor(runId, item).id

        assertFailsWith<IllegalArgumentException>("Tom ID skal avvises") {
            s.repo.resolveDeficiency(responseId, userId = "", newReading = "200")
        }
        assertFailsWith<IllegalArgumentException>("Ukjent ID skal avvises") {
            s.repo.resolveDeficiency(responseId, userId = "9999", newReading = "200")
        }
        assertEquals(0L, s.responseFor(runId, item).resolved)
    }

    @Test
    fun `målepunkt kan ikke løses uten ny verdi`() = runTest {
        val s = scenario()
        val item = oksygen(s)
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA, reading = "150")
        val responseId = s.responseFor(runId, item).id

        assertFailsWith<IllegalArgumentException> {
            s.repo.resolveDeficiency(responseId, s.userId, newReading = null)
        }
        assertFailsWith<IllegalArgumentException> {
            s.repo.resolveDeficiency(responseId, s.userId, newReading = "tom")
        }
    }

    @Test
    fun `ny verdi utenfor grensa løser ikke avviket`() = runTest {
        val s = scenario()
        val item = oksygen(s)
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA, reading = "150")
        val responseId = s.responseFor(runId, item).id

        assertFailsWith<IllegalStateException> {
            s.repo.resolveDeficiency(responseId, s.userId, newReading = "170")
        }
        assertEquals(0L, s.responseFor(runId, item).resolved, "Avviket må fortsatt være åpent")
    }

    @Test
    fun `gyldig ny verdi løser avviket og bevarer den gamle`() = runTest {
        val s = scenario()
        val item = oksygen(s)
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA, reading = "150")
        val responseId = s.responseFor(runId, item).id

        s.repo.resolveDeficiency(responseId, s.userId, newReading = "210")

        val response = s.responseFor(runId, item)
        assertEquals(1L, response.resolved)
        assertEquals("MANUAL", response.resolvedVia)
        assertEquals("150", response.reading, "Gammel verdi må bevares i historikken")
        assertEquals("210", response.resolvedReading)
        assertEquals(s.userId, response.resolvedByUserId)
        assertNotNull(response.resolvedAt)
    }

    @Test
    fun `avvik uten måling løses uten verdi`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.ODELAGT, comment = "Trykkmåler knust")
        val responseId = s.responseFor(runId, item).id

        s.repo.resolveDeficiency(responseId, s.userId)

        val response = s.responseFor(runId, item)
        assertEquals(1L, response.resolved)
        assertNull(response.resolvedReading)
    }

    @Test
    fun `et avvik kan ikke løses to ganger`() = runTest {
        val s = scenario()
        val item = s.addItem("Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.NEI)
        val responseId = s.responseFor(runId, item).id
        s.repo.resolveDeficiency(responseId, s.userId)

        assertFailsWith<IllegalStateException> {
            s.repo.resolveDeficiency(responseId, s.userId)
        }
    }
}
