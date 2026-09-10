package org.example.project.model

/**
 * Grensene for et målepunkt, f.eks. oksygentrykk 180–250 bar.
 *
 * Grensene beskrives fire steder i appen: i feilmeldingen når en avlest verdi
 * er utenfor, i kommentaren som automatisk settes på avviket, i feltet der et
 * avvik løses med ny måling, og i punktlista i maleditoren. Teksten hørte
 * hjemme ett sted – ellers driver formuleringene fra hverandre, og mannskapet
 * møter «(min 180) (maks 250)» ett sted og «min 180, maks 250» et annet.
 *
 * Én av grensene kan stå alene: et drivstoffnivå har en nedre grense, ikke en
 * øvre.
 */
data class MeasurementLimits(
    val min: Double? = null,
    val max: Double? = null,
) {
    val isEmpty: Boolean get() = min == null && max == null

    /** Sant når verdien er innenfor de grensene som faktisk er satt. */
    fun contains(value: Double): Boolean =
        (min == null || value >= min) && (max == null || value <= max)

    /** «(min 180, maks 250)», eller tom streng når punktet ikke har grenser. */
    fun describe(): String =
        if (isEmpty) "" else " (${parts().joinToString(", ")})"

    /** «min 180 · maks 250» – uten parentes, til punktlista i maleditoren. */
    fun describeInline(): String = parts().joinToString(" · ")

    private fun parts(): List<String> = buildList {
        min?.let { add("min ${formatNumber(it)}") }
        max?.let { add("maks ${formatNumber(it)}") }
    }
}
