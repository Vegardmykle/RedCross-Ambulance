package org.example.project.model

/**
 * Livsløpet til en kontroll.
 *
 * IN_PROGRESS dekker også vakter der før-delen er signert – vakta er ikke
 * over før avslutningen også er signert, og kontrollen må kunne endres helt
 * fram til det.
 */
enum class RunStatus(val db: String, val label: String) {
    IN_PROGRESS("IN_PROGRESS", "Pågår"),
    COMPLETED("COMPLETED", "Signert"),
    EXPIRED("EXPIRED", "Utløpt – ikke signert");

    companion object {
        /**
         * Ukjent verdi gir null, ikke en standardverdi.
         *
         * Statusen krysser en synkgrense: en enhet med en eldre appversjon kan
         * møte en status som ble innført senere. Ble den tolket som
         * IN_PROGRESS, ville en kontroll som egentlig er lukket plutselig
         * kunne endres og signeres på nytt. Kallstedet må derfor ta stilling
         * til det ukjente tilfellet selv.
         */
        fun fromDb(value: String?): RunStatus? = entries.firstOrNull { it.db == value }
    }
}
