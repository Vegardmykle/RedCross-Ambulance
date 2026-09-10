package org.example.project.presentation

import database.ChecklistItem
import database.ChecklistResponse
import database.ChecklistRun
import org.example.project.model.ChecklistPhase
import org.example.project.model.ItemResult

/**
 * Fremdrift i én del av kontrollen.
 *
 * En tom del er ikke fullført. Uten det skillet ville en liste uten punkter
 * sett signeringsklar ut, og en signatur på ingenting ser i arkivet ut som en
 * gjennomført kontroll.
 */
data class PhaseProgress(val answered: Int, val total: Int) {
    val isEmpty: Boolean get() = total == 0
    val isComplete: Boolean get() = total > 0 && answered == total
    val fraction: Float get() = if (total == 0) 0f else answered.toFloat() / total
}

/** Ett avvik slik det oppsummeres før mannskapet signerer. */
data class DeficiencySummary(
    val itemId: String,
    val title: String,
    val resultLabel: String,
    val comment: String?,
)

/**
 * Alt sjekklisteskjermen viser, utledet ett sted for begge plattformene.
 *
 * Dette lå tidligere som rundt 120 linjer i Compose-skjermen og de samme 120
 * linjene igjen i SwiftUI-viewet. Kopiene hadde begynt å skli fra hverandre,
 * og ingen av dem kunne nås av en test. Dette er en sikkerhetskritisk skjerm:
 * den avgjør om mannskapet får signere på at bilen er kontrollert.
 */
data class ChecklistRunState(
    /**
     * Punktene i hovedlista som kontrolleres før vakt. Punkter som ligger i
     * sekker er ikke med – de vises i hvert sitt sammenleggbare kort – men de
     * teller i [before] og [overall].
     */
    val beforeItems: List<ChecklistItem>,

    /** Alle avslutningspunkter, også de som ligger i sekker. */
    val afterItems: List<ChecklistItem>,

    val before: PhaseProgress,
    val after: PhaseProgress,
    val overall: PhaseProgress,

    /**
     * Om lista har egne avslutningspunkter og dermed signeres i to trinn.
     * Ukentlige og månedlige kontroller har det ikke, og signeres én gang.
     */
    val hasAfterPhase: Boolean,

    val beforeSignedAt: Long?,

    /** Avvikene i før-delen, til kvitteringen når før-delen signeres. */
    val beforeDeficiencies: List<DeficiencySummary>,

    /** Avvikene i hele kontrollen, til kvitteringen når vakta avsluttes. */
    val allDeficiencies: List<DeficiencySummary>,
) {
    val beforeSigned: Boolean get() = beforeSignedAt != null

    /**
     * Har lista en avslutningsdel, dekker denne knappen bare før-delen.
     * Ellers signerer den hele kontrollen ferdig i ett trinn.
     */
    val canSignBefore: Boolean
        get() = if (hasAfterPhase) before.isComplete else overall.isComplete

    /**
     * Vakta kan avsluttes når før-delen er signert og alt er besvart.
     * Repositoryet håndhever det samme – dette styrer bare om knappen er
     * aktiv, slik at mannskapet ikke møter en feilmelding de ikke kan handle på.
     */
    val canComplete: Boolean get() = beforeSigned && overall.isComplete
}

/**
 * Bygger tilstanden fra det databasen faktisk inneholder.
 *
 * [items] må være hele treet – hovedliste og sekker – slik
 * `getItemsForTemplateTree` gir det. Sekkepunktene kjennes igjen på at de
 * hører til en annen mal enn [rootTemplateId].
 *
 * Rekkefølgen følger sortOrder, som er rekkefølgen utstyret ligger i bilen.
 * Besvarte punkter blir stående der de er: tidligere sank de til bunnen, men
 * da flyttet innholdet seg under fingeren på mannskapet – du sikter på ett
 * punkt, lista hopper, og du treffer et annet. Med hansker, i bevegelse,
 * eller for noen med skjelvinger er et feiltrykk på et sikkerhetsskjema dyrt.
 */
fun checklistRunState(
    rootTemplateId: String,
    items: List<ChecklistItem>,
    responses: List<ChecklistResponse>,
    run: ChecklistRun?,
): ChecklistRunState {
    val responseByItem = responses.associateBy { it.itemId }
    fun isAnswered(item: ChecklistItem) = responseByItem[item.id] != null
    fun phaseOf(item: ChecklistItem) = ChecklistPhase.fromDb(item.phase)

    val beforeAll = items.filter { phaseOf(it) == ChecklistPhase.BEFORE }
    val afterAll = items.filter { phaseOf(it) == ChecklistPhase.AFTER }

    return ChecklistRunState(
        beforeItems = beforeAll.filter { it.templateId == rootTemplateId },
        afterItems = afterAll,
        before = PhaseProgress(beforeAll.count(::isAnswered), beforeAll.size),
        after = PhaseProgress(afterAll.count(::isAnswered), afterAll.size),
        overall = PhaseProgress(items.count(::isAnswered), items.size),
        hasAfterPhase = afterAll.isNotEmpty(),
        beforeSignedAt = run?.beforeSignedAt,
        beforeDeficiencies = deficiencySummaries(beforeAll, responseByItem),
        allDeficiencies = deficiencySummaries(items, responseByItem),
    )
}

/**
 * Punktene som ikke er meldt i orden, med teksten mannskapet bekrefter.
 * Ubesvarte punkter er ikke avvik – de er bare ikke kontrollert ennå.
 */
private fun deficiencySummaries(
    items: List<ChecklistItem>,
    responseByItem: Map<String, ChecklistResponse>,
): List<DeficiencySummary> = items.mapNotNull { item ->
    val response = responseByItem[item.id] ?: return@mapNotNull null
    if (response.result == ItemResult.JA.db) return@mapNotNull null
    DeficiencySummary(
        itemId = item.id,
        title = item.title,
        resultLabel = ItemResult.entries
            .firstOrNull { it.db == response.result }?.label ?: response.result,
        comment = response.comment,
    )
}
