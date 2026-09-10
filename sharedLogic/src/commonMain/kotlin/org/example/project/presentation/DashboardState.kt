package org.example.project.presentation

import org.example.project.model.TemplateType

/**
 * Om en fast kontroll er gjort i inneværende periode for dette kjøretøyet.
 */
data class ShiftCheckStatus(
    val type: TemplateType,
    val lastCompletedAt: Long?,
    val isDone: Boolean,
)

/**
 * Statusen dashbordet viser for valgt ambulanse.
 *
 * Regelen lå tidligere bare i Compose-koden; iOS hadde ingen og viste et
 * fastspikret «Ikke påbegynt» uansett tilstand. Nå leser begge plattformene
 * det samme.
 */
data class DashboardState(
    val daily: ShiftCheckStatus,
    val weekly: ShiftCheckStatus,
    val monthly: ShiftCheckStatus,
) {
    fun statusFor(type: TemplateType): ShiftCheckStatus? = when (type) {
        TemplateType.DAILY -> daily
        TemplateType.WEEKLY -> weekly
        TemplateType.MONTHLY -> monthly
        TemplateType.BAG -> null
    }
}

/**
 * Avgjør hvilke faste kontroller som er gjort i inneværende periode.
 *
 * Grensene sendes inn i stedet for å leses fra klokka her. Det gjør regelen
 * testbar uten å manipulere systemtiden – testdekningsnotatet peker på
 * nettopp mangelen på klokkeinjeksjon som et hull.
 *
 * Periodene er kalenderbaserte: en ukessjekk gjort mandag gjelder ut uka, og
 * en månedssjekk gjelder ut måneden. Et glidende vindu ville gjort at en
 * kontroll «gikk ut» midt i uka, på et tidspunkt ingen kunne forutsi.
 *
 * [latestCompletedAt] skal gjelde ett kjøretøy. Sjekklista dokumenterer at en
 * bestemt bil er kontrollert, så en kontroll av en annen bil sier ingenting
 * om denne.
 */
fun dashboardState(
    latestCompletedAt: Map<String, Long>,
    startOfToday: Long,
    startOfWeek: Long,
    startOfMonth: Long,
): DashboardState {
    fun status(type: TemplateType, since: Long): ShiftCheckStatus {
        val completedAt = latestCompletedAt[type.db]
        return ShiftCheckStatus(
            type = type,
            lastCompletedAt = completedAt,
            isDone = completedAt != null && completedAt >= since,
        )
    }
    return DashboardState(
        daily = status(TemplateType.DAILY, startOfToday),
        weekly = status(TemplateType.WEEKLY, startOfWeek),
        monthly = status(TemplateType.MONTHLY, startOfMonth),
    )
}
