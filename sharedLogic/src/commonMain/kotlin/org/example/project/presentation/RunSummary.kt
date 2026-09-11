package org.example.project.presentation

import database.GetResponsesWithItemsForRun
import org.example.project.model.ItemResult
import org.example.project.model.ResolvedVia

/**
 * Hvordan avvikene i en kontroll står nå. Styrer farge og ikon i arkivet.
 *
 * Utfallet skilles fra teksten fordi fargene er plattformspesifikke – Compose
 * bruker RkOrange, SwiftUI bruker .orange – mens ordlyden skal være identisk.
 */
enum class DeviationOutcome {
    /**
     * Ingenting ble meldt.
     *
     * Heter ikke NONE fordi det kolliderer med Optional.none i Swift. SKIE
     * ville da døpt om identifikatoren, og et omdøpt navn endrer seg igjen
     * hvis konflikten senere løses – da brekker Swift-koden uten forvarsel.
     */
    NO_DEVIATIONS,

    /** Minst ett avvik står fortsatt åpent. */
    OPEN,

    /** Ingen åpne, men noe ble videreført til en senere kontroll. */
    CARRIED_OVER,

    /** Alt som ble meldt er fulgt opp. */
    RESOLVED,
}

data class DeviationSummary(
    val outcome: DeviationOutcome,
    val text: String,
)

/**
 * Oppsummerer avvikene i én kontroll.
 *
 * Regnestykket er ikke opplagt: et videreført avvik teller som lukket i
 * databasen, men det er ikke løst – det er meldt på nytt i en senere
 * kontroll. Bare differansen er faktisk åpen. Denne logikken lå ordrett i
 * både Compose og SwiftUI, inkludert den norske flertallsbøyningen.
 */
fun deviationSummary(total: Long, resolved: Long, superseded: Long): DeviationSummary {
    val open = total - resolved - superseded
    return when {
        total == 0L -> DeviationSummary(DeviationOutcome.NO_DEVIATIONS, "Ingen avvik")

        open > 0L -> {
            val parts = buildList {
                add(if (open == 1L) "1 åpent avvik" else "$open åpne avvik")
                if (resolved > 0L) add("$resolved løst")
                if (superseded > 0L) add("$superseded videreført")
            }
            DeviationSummary(DeviationOutcome.OPEN, parts.joinToString(" · "))
        }

        superseded > 0L -> DeviationSummary(
            DeviationOutcome.CARRIED_OVER,
            when {
                resolved > 0L -> "$superseded videreført · $resolved løst"
                superseded == 1L -> "1 avvik videreført"
                else -> "$superseded avvik videreført"
            },
        )

        else -> DeviationSummary(
            DeviationOutcome.RESOLVED,
            if (total == 1L) "Avviket er løst" else "Alle $total avvik løst",
        )
    }
}

/** Hvordan ett enkelt svar står i arkivet. */
enum class ResponseOutcome { OK, DEVIATION, RESOLVED, CARRIED_OVER }

data class ResponseBadge(
    val text: String,
    val outcome: ResponseOutcome,
)

/**
 * Merkelappen på ett svar i en tidligere kontroll.
 *
 * Videreført sjekkes før løst: begge har resolved = 1 i databasen, men et
 * videreført avvik er ikke rettet, og å vise «Løst» på det ville vært
 * misvisende i et dokument som skal si hva som faktisk ble gjort.
 */
fun responseBadge(response: GetResponsesWithItemsForRun): ResponseBadge {
    val via = ResolvedVia.fromDb(response.resolvedVia)
    val label = ItemResult.entries
        .firstOrNull { it.db == response.result }?.label ?: response.result
    return when {
        via == ResolvedVia.SUPERSEDED -> ResponseBadge("Videreført", ResponseOutcome.CARRIED_OVER)
        response.resolved != 0L -> ResponseBadge("Løst", ResponseOutcome.RESOLVED)
        response.result == ItemResult.JA.db -> ResponseBadge(label, ResponseOutcome.OK)
        else -> ResponseBadge(label, ResponseOutcome.DEVIATION)
    }
}

/**
 * Forklaringen under et lukket avvik: hva det var, hvordan det ble lukket, og
 * eventuell ny måleverdi.
 *
 * Tidspunktet sendes inn ferdig formatert. Datoformatering er
 * plattformspesifikk – Android bruker SimpleDateFormat, iOS DateFormatter –
 * mens ordlyden rundt skal være den samme.
 */
fun resolutionText(
    response: GetResponsesWithItemsForRun,
    resolvedAtText: String,
): String {
    val label = ItemResult.entries
        .firstOrNull { it.db == response.result }?.label ?: response.result
    val how = when (ResolvedVia.fromDb(response.resolvedVia)) {
        ResolvedVia.RECHECK -> "OK ved senere kontroll"
        ResolvedVia.SUPERSEDED -> "videreført til senere kontroll"
        else -> response.resolvedByName?.let { "løst av $it" } ?: "løst manuelt"
    }
    return buildString {
        append("Var ${label.lowercase()} · $how $resolvedAtText")
        response.resolvedReading?.takeIf { it.isNotEmpty() }?.let {
            append(" · ny verdi $it ${response.unit ?: ""}")
        }
    }
}
