package org.example.project.sync

import org.example.project.data.inMemoryDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Grensa for inkrementell henting av kjøringer og svar.
 *
 * Regelen har en asymmetrisk kostnad: settes grensa for tidlig hentes noen
 * ekstra dokumenter, settes den for sent hentes en rad aldri. En kontroll som
 * er signert på en annen enhet og aldri kommer fram er stille datatap i et
 * system som skal dokumentere at bilen er kontrollert.
 */
class PullWatermarkTest {

    private val service = FirebaseSyncService(inMemoryDatabase())

    @Test
    fun `uten tidligere henting hentes alt`() {
        assertNull(
            service.pullSinceFor(null),
            "Første synk, eller en nyoppgradert enhet, vet ikke hva den har gått glipp av",
        )
    }

    @Test
    fun `grensa legges et dogn for forrige henting`() {
        val lastPull = 1_700_000_000_000
        val since = service.pullSinceFor(lastPull)!!

        assertEquals(
            FirebaseSyncService.PULL_OVERLAP_MILLIS, lastPull - since,
            "Overlappen dekker klokkeavvik mellom enheter",
        )
        assertTrue(since < lastPull)
    }

    @Test
    fun `grensa gar aldri under null`() {
        // En enhet med feilstilt klokke kan ha bokført en henting nær epoch.
        // Et negativt tidsstempel ville gitt en spørring ingen rad matcher.
        assertEquals(0, service.pullSinceFor(0))
        assertEquals(0, service.pullSinceFor(1000))
    }

    @Test
    fun `overlappen er romslig nok for realistisk klokkeavvik`() {
        assertTrue(
            FirebaseSyncService.PULL_OVERLAP_MILLIS >= 60 * 60 * 1000,
            "Under en time er for stramt – enheter kan drifte mer enn det mellom synker",
        )
    }
}
