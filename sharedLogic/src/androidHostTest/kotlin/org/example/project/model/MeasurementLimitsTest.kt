package org.example.project.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Grensene beskrives både i feilmeldinger og i punktlista, og brukes til å
 * avgjøre om en avlest verdi er et avvik. Testene låser begge deler, siden
 * teksten er det mannskapet leser når de lurer på hvorfor en verdi ble
 * underkjent.
 */
class MeasurementLimitsTest {

    @Test
    fun `verdi innenfor begge grenser godtas`() {
        val limits = MeasurementLimits(min = 180.0, max = 250.0)
        assertTrue(limits.contains(180.0), "Grensa selv er innenfor")
        assertTrue(limits.contains(250.0), "Grensa selv er innenfor")
        assertTrue(limits.contains(215.0))
        assertFalse(limits.contains(179.9))
        assertFalse(limits.contains(250.1))
    }

    @Test
    fun `en grense kan sta alene`() {
        // Drivstoffnivå har en nedre grense, ikke en øvre
        val onlyMin = MeasurementLimits(min = 50.0)
        assertTrue(onlyMin.contains(100.0))
        assertFalse(onlyMin.contains(49.0))

        val onlyMax = MeasurementLimits(max = 8.0)
        assertTrue(onlyMax.contains(-30.0))
        assertFalse(onlyMax.contains(9.0))
    }

    @Test
    fun `uten grenser er alt innenfor`() {
        val none = MeasurementLimits()
        assertTrue(none.isEmpty)
        assertTrue(none.contains(-1.0))
        assertEquals("", none.describe(), "Ingen grenser gir ingen tekst")
    }

    @Test
    fun `grensene beskrives likt overalt`() {
        assertEquals(" (min 180, maks 250)", MeasurementLimits(180.0, 250.0).describe())
        assertEquals(" (min 50)", MeasurementLimits(min = 50.0).describe())
        assertEquals(" (maks 8)", MeasurementLimits(max = 8.0).describe())
        assertEquals("min 180 · maks 250", MeasurementLimits(180.0, 250.0).describeInline())
    }

    @Test
    fun `hele tall vises uten desimaler`() {
        assertEquals("180", formatNumber(180.0))
        assertEquals("2.5", formatNumber(2.5))
        assertEquals(" (min 2.5)", MeasurementLimits(min = 2.5).describe())
    }

    @Test
    fun `tallfilteret slipper gjennom ett desimaltegn`() {
        assertEquals("180", filterNumeric("180"))
        assertEquals("18.0", filterNumeric("18,0"), "Komma er det mannskapet taster")
        assertEquals("18.05", filterNumeric("18.0.5"), "Bare det første desimaltegnet")
        assertEquals("180", filterNumeric("1a8b0c"))
    }

    @Test
    fun `halvferdige tall normaliseres for lagring`() {
        assertEquals("180", normalizeNumber("180."))
        assertEquals("0.1", normalizeNumber(".1"))
        assertEquals("180", normalizeNumber("  180  "))
        assertEquals(null, normalizeNumber(""), "Tomt felt skal ikke lagres")
        assertEquals(null, normalizeNumber("."), "Bare et punktum er ikke et tall")
    }
}
