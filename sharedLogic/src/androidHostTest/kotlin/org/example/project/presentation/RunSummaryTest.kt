package org.example.project.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ordlyden i arkivet.
 *
 * Regnestykket bak «åpne avvik» er ikke opplagt: et videreført avvik står som
 * lukket i databasen, men er ikke rettet – det er meldt på nytt senere. Teller
 * man det som løst, ser arkivet ryddigere ut enn virkeligheten.
 */
class RunSummaryTest {

    private fun summary(total: Long, resolved: Long = 0, superseded: Long = 0) =
        deviationSummary(total, resolved, superseded)

    @Test
    fun `ingen avvik meldt`() {
        val s = summary(total = 0)
        assertEquals(DeviationOutcome.NO_DEVIATIONS, s.outcome)
        assertEquals("Ingen avvik", s.text)
    }

    @Test
    fun `apne avvik boyes riktig`() {
        assertEquals("1 åpent avvik", summary(total = 1).text)
        assertEquals("3 åpne avvik", summary(total = 3).text)
        assertEquals(DeviationOutcome.OPEN, summary(total = 1).outcome)
    }

    @Test
    fun `apne avvik nevner ogsa de som er ryddet unna`() {
        assertEquals(
            "1 åpent avvik · 2 løst · 1 videreført",
            summary(total = 4, resolved = 2, superseded = 1).text,
        )
    }

    @Test
    fun `videreført er ikke det samme som lost`() {
        val s = summary(total = 1, superseded = 1)
        assertEquals(DeviationOutcome.CARRIED_OVER, s.outcome)
        assertEquals("1 avvik videreført", s.text)

        assertEquals("2 avvik videreført", summary(total = 2, superseded = 2).text)
        assertEquals(
            "1 videreført · 1 løst",
            summary(total = 2, resolved = 1, superseded = 1).text,
        )
    }

    @Test
    fun `alt fulgt opp`() {
        assertEquals(DeviationOutcome.RESOLVED, summary(total = 1, resolved = 1).outcome)
        assertEquals("Avviket er løst", summary(total = 1, resolved = 1).text)
        assertEquals("Alle 3 avvik løst", summary(total = 3, resolved = 3).text)
    }

    @Test
    fun `apne avvik vinner over videreførte i visningen`() {
        // Er noe fortsatt åpent, er det den viktigste beskjeden
        val s = summary(total = 3, resolved = 0, superseded = 1)
        assertEquals(DeviationOutcome.OPEN, s.outcome)
        assertEquals("2 åpne avvik · 1 videreført", s.text)
    }
}
