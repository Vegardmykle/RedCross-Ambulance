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

enum class TemplateType(val db: String, val label: String) {
    DAILY("DAILY", "Daglig"),
    WEEKLY("WEEKLY", "Ukentlig"),
    MONTHLY("MONTHLY", "Månedlig"),
    BAG("BAG", "Sekk/taske");

    companion object {
        fun fromDb(value: String): TemplateType = entries.first { it.db == value }
    }
}
