package org.example.project.model

/**
 * Hvordan et avvik ble lukket.
 *
 * Skillet betyr noe i arkivet: RECHECK og SUPERSEDED skjer automatisk når
 * punktet kontrolleres på nytt, mens MANUAL krever at noen signerer for at
 * det faktisk er rettet. Et videreført avvik (SUPERSEDED) er ikke løst – det
 * er erstattet av en nyere melding om det samme, og kjeden spores tilbake til
 * den første meldingen.
 */
enum class ResolvedVia(val db: String) {
    MANUAL("MANUAL"),
    RECHECK("RECHECK"),
    SUPERSEDED("SUPERSEDED");

    companion object {
        /** Ukjent verdi gir null – se [RunStatus.fromDb] for hvorfor. */
        fun fromDb(value: String?): ResolvedVia? = entries.firstOrNull { it.db == value }
    }
}
