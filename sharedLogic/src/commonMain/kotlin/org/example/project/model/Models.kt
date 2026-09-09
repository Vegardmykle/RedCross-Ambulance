package org.example.project.model

/** Resultat per sjekkpunkt: ja / nei / mangelfull / ødelagt. */
enum class ItemResult(val db: String, val label: String) {
    JA("JA", "Ja"),
    NEI("NEI", "Nei"),
    MANGELFULL("MANGELFULL", "Mangelfull"),
    ODELAGT("ODELAGT", "Ødelagt");

    companion object {
        fun fromDb(value: String): ItemResult = entries.first { it.db == value }
    }
}

/**
 * Åpent avvik til Mangler-siden. Hvis avviket er videreført fra tidligere
 * kontroller, peker firstReportedAt/firstReportedByName på den opprinnelige meldingen.
 */
data class OpenDeficiency(
    val id: String,
    val result: String,
    val comment: String?,
    val reading: String?,
    val checkedAt: Long,
    val itemTitle: String,
    val requiresValue: Long,
    val unit: String?,
    val minValue: Double?,
    val maxValue: Double?,
    val listName: String,
    val callSign: String,
    val signedByName: String?,
    val firstReportedAt: Long?,
    val firstReportedByName: String?,
)

/**
 * Når i vakta et punkt skal kontrolleres.
 *
 * Punkter som hører til avslutningen – oksygen skrudd av, vask, km notert –
 * lå tidligere blandet sammen med de som kontrolleres før vakta. Mannskapet
 * opplevde da at de måtte begynne på nytt etter vakta.
 */
enum class ChecklistPhase(val db: String, val label: String) {
    BEFORE("BEFORE", "Før vakt"),
    AFTER("AFTER", "Etter vakt");

    companion object {
        /** Ukjente verdier tolkes som BEFORE, så et punkt aldri blir usynlig. */
        fun fromDb(value: String?): ChecklistPhase =
            entries.firstOrNull { it.db == value } ?: BEFORE
    }
}

enum class TemplateType(val db: String, val label: String) {
    DAILY("DAILY", "Daglig"),
    WEEKLY("WEEKLY", "Ukentlig"),
    MONTHLY("MONTHLY", "Månedlig"),
    BAG("BAG", "Sekk/taske");

    companion object {
        fun fromDb(value: String): TemplateType = entries.first { it.db == value }
    }
}
