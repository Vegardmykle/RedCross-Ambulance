package org.example.project.presentation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.data.scenario
import org.example.project.model.ItemResult
import org.example.project.model.TemplateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dashbordets «utført»-merker.
 *
 * Grensene sendes inn, så periodelogikken kan testes uten å røre systemklokka.
 * Testdekningsnotatet fører opp mangelen på klokkeinjeksjon som et hull –
 * dette lukker det for denne regelen.
 */
class DashboardStateTest {

    // Faste tidspunkter, ikke klokka: 15. mars er en lørdag i 2025,
    // uka begynte mandag 10., måneden 1.
    private val startOfMonth = 1_000_000L
    private val startOfWeek = 2_000_000L
    private val startOfToday = 3_000_000L

    private fun state(vararg latest: Pair<TemplateType, Long>) = dashboardState(
        latestCompletedAt = latest.associate { (type, at) -> type.db to at },
        startOfToday = startOfToday,
        startOfWeek = startOfWeek,
        startOfMonth = startOfMonth,
    )

    @Test
    fun `kontroll gjort i dag teller som utfort`() {
        val s = state(TemplateType.DAILY to startOfToday + 1)
        assertTrue(s.daily.isDone)
    }

    @Test
    fun `gardagens kontroll teller ikke for i dag`() {
        val s = state(TemplateType.DAILY to startOfToday - 1)
        assertFalse(s.daily.isDone, "Bilen skal kontrolleres på nytt hver dag")
        assertEquals(startOfToday - 1, s.daily.lastCompletedAt)
    }

    @Test
    fun `kontroll utfort akkurat pa periodegrensa teller med`() {
        assertTrue(state(TemplateType.DAILY to startOfToday).daily.isDone)
        assertTrue(state(TemplateType.WEEKLY to startOfWeek).weekly.isDone)
        assertTrue(state(TemplateType.MONTHLY to startOfMonth).monthly.isDone)
    }

    @Test
    fun `ukessjekk tidligere i uka gjelder fortsatt`() {
        // Mandagens ukessjekk skal gjelde ut uka, ikke i sju døgn fra
        // klokkeslettet den ble signert
        val s = state(TemplateType.WEEKLY to startOfWeek + 1)
        assertTrue(s.weekly.isDone)
        assertFalse(s.daily.isDone, "Ukessjekken sier ingenting om dagens kontroll")
    }

    @Test
    fun `forrige ukes sjekk teller ikke`() {
        assertFalse(state(TemplateType.WEEKLY to startOfWeek - 1).weekly.isDone)
    }

    @Test
    fun `manedssjekk tidligere i maneden gjelder fortsatt`() {
        val s = state(TemplateType.MONTHLY to startOfMonth + 1)
        assertTrue(s.monthly.isDone)
    }

    @Test
    fun `aldri utfort gir ikke utfort og ingen dato`() {
        val s = state()
        assertFalse(s.daily.isDone)
        assertFalse(s.weekly.isDone)
        assertFalse(s.monthly.isDone)
        assertNull(s.daily.lastCompletedAt)
    }

    @Test
    fun `sekker har ingen periodestatus`() {
        assertNull(state().statusFor(TemplateType.BAG))
    }

    // ---------- Mot databasen ----------

    @Test
    fun `utfort kontroll pa en bil gjor ikke den andre bilen utfort`() = runTest {
        val s = scenario()
        val other = s.repo.addAmbulance(callSign = "Ambulanse 2", registrationNumber = "EL54321")
        val item = s.repo.addItem(s.templateId, "Brannslukker")

        val runId = s.repo.startOrResumeRun(s.templateId, s.ambulanceId).id
        s.repo.setResponse(runId, item, ItemResult.JA)
        s.repo.completeRun(runId, s.userId)

        assertEquals(
            1, s.repo.latestCompletedByType(s.ambulanceId).first().size,
            "Bilen som ble kontrollert har en fullført kontroll",
        )
        assertTrue(
            s.repo.latestCompletedByType(other).first().isEmpty(),
            "Sjekklista dokumenterer én bestemt bil – den andre er ikke kontrollert",
        )
    }

    @Test
    fun `bare fullforte kontroller teller`() = runTest {
        val s = scenario()
        val item = s.repo.addItem(s.templateId, "Brannslukker")
        val runId = s.startRun()
        s.repo.setResponse(runId, item, ItemResult.JA)

        assertTrue(
            s.repo.latestCompletedByType(s.ambulanceId).first().isEmpty(),
            "En påbegynt kontroll er ikke dokumentasjon på at bilen er sjekket",
        )
    }
}
