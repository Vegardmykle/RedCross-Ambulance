package org.example.project.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import database.Ambulance
import database.AppLink
import database.ChecklistItem
import database.ChecklistResponse
import database.ChecklistRun
import database.ChecklistTemplate
import database.Document
import database.GetOpenDeficiencies
import database.GetRecentRuns
import database.GetResponsesWithItemsForRun
import database.GetRunsAwaitingClosure
import database.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.example.project.db.AppDatabase
import org.example.project.model.ChecklistPhase
import org.example.project.model.ItemResult
import org.example.project.model.MeasurementLimits
import org.example.project.model.OpenDeficiency
import org.example.project.model.ResolvedVia
import org.example.project.model.RunStatus
import org.example.project.model.TemplateType
import kotlin.coroutines.cancellation.CancellationException
import org.example.project.util.currentTimeMillis
import org.example.project.util.randomId

/**
 * Alt UI-laget trenger for å lese og skrive sjekklister.
 * Lesing eksponeres som Flow (oppdateres automatisk ved endringer),
 * skriving er suspend-funksjoner.
 */
class ChecklistRepository(private val db: AppDatabase) {

    companion object {
        /**
         * Hvor lenge en påbegynt kontroll regnes som en pågående vakt.
         *
         * 16 timer dekker døgnvakter og nattevakter med margin, uten at en
         * glemt kontroll blir stående i dagevis. Grensa er bevisst en varighet
         * og ikke et døgnskille – nattevakter krysser midnatt.
         */
        const val SHIFT_TIMEOUT_MILLIS: Long = 16 * 60 * 60 * 1000
    }

    // ---------- Maler ----------

    fun topLevelTemplates(): Flow<List<ChecklistTemplate>> =
        db.checklistTemplateQueries.getAllTopLevelTemplates()
            .asFlow().mapToList(Dispatchers.Default)

    fun bagsFor(templateId: String): Flow<List<ChecklistTemplate>> =
        db.checklistTemplateQueries.getBagsForTemplate(templateId)
            .asFlow().mapToList(Dispatchers.Default)

    fun itemsFor(templateId: String): Flow<List<ChecklistItem>> =
        db.checklistItemQueries.getItemsByTemplateId(templateId)
            .asFlow().mapToList(Dispatchers.Default)

    /**
     * Alle punkter i lista inkludert sekkene, i den rekkefølgen utstyret
     * ligger i bilen.
     *
     * Sjekklisteskjermen trenger hele settet for å telle fremdrift og avgjøre
     * om alt er besvart. Å samle det opp fra sekkekortene mens de tegnes
     * fungerer bare så lenge de faktisk tegnes – og sekkene skjules når
     * før-delen er signert.
     */
    fun itemsForTemplateTree(templateId: String): Flow<List<ChecklistItem>> =
        db.checklistItemQueries.getItemsForTemplateTree(templateId)
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun createTemplate(
        name: String,
        type: TemplateType,
        parentId: String? = null,
    ): String = withContext(Dispatchers.Default) {
        val id = randomId()
        db.checklistTemplateQueries.insertTemplate(id, name, type.db, parentId, 0, currentTimeMillis())
        id
    }

    suspend fun renameTemplate(id: String, name: String) =
        withContext(Dispatchers.Default) {
            db.checklistTemplateQueries.updateTemplateName(id, name, currentTimeMillis())
        }

    /**
     * Flytter en sekk/taske til en annen hovedliste. Punktene følger med.
     *
     * Historiske kontroller påvirkes ikke – de peker på svarene som ble gitt.
     * Men en påbegynt, usignert kontroll på mottakerlista vil etter flyttingen
     * kreve svar også på punktene i sekken, siden alle punkter må besvares før
     * signering.
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class, CancellationException::class)
    suspend fun moveBag(bagId: String, newParentId: String) = withContext(Dispatchers.Default) {
        require(bagId != newParentId) { "En sekk kan ikke være sin egen hovedliste" }

        val bag = db.checklistTemplateQueries.getTemplateById(bagId).executeAsOneOrNull()
        checkNotNull(bag) { "Fant ikke sekken" }
        check(bag.type == TemplateType.BAG.db) { "Bare sekker kan flyttes" }

        val parent = db.checklistTemplateQueries.getTemplateById(newParentId).executeAsOneOrNull()
        checkNotNull(parent) { "Fant ikke lista sekken skal flyttes til" }
        check(parent.parentId == null) { "En sekk kan ikke legges inni en annen sekk" }
        check(parent.deleted == 0L) { "Lista er slettet" }

        val next = (db.checklistTemplateQueries
            .maxBagSortOrderForParent(newParentId).executeAsOne().maxSort ?: 0L) + 1

        db.checklistTemplateQueries.moveTemplateToParent(
            bagId, newParentId, next, currentTimeMillis(),
        )
    }

    /** Sletter (soft) mal, dens punkter og eventuelle sekker med innhold. */
    suspend fun deleteTemplate(id: String) = withContext(Dispatchers.Default) {
        val now = currentTimeMillis()
        db.transaction {
            db.checklistTemplateQueries.getBagsForTemplate(id).executeAsList().forEach { bag ->
                db.checklistItemQueries.deleteItemsForTemplate(bag.id, now)
                db.checklistTemplateQueries.deleteTemplate(bag.id, now)
            }
            db.checklistItemQueries.deleteItemsForTemplate(id, now)
            db.checklistTemplateQueries.deleteTemplate(id, now)
        }
    }

    suspend fun addItem(
        templateId: String,
        title: String,
        description: String? = null,
        requiresValue: Boolean = false,
        unit: String? = null,
        minValue: Double? = null,
        maxValue: Double? = null,
        phase: ChecklistPhase = ChecklistPhase.BEFORE,
    ): String = withContext(Dispatchers.Default) {
        val id = randomId()
        val next = (db.checklistItemQueries.maxSortOrderForTemplate(templateId)
            .executeAsOne().maxSort ?: 0L) + 1
        db.checklistItemQueries.insertItem(
            id, templateId, title, description,
            if (requiresValue) 1L else 0L, unit, minValue, maxValue, next,
            phase.db, currentTimeMillis(),
        )
        id
    }

    /** Redigering av punkt – inkludert grenseverdier (settes til null for å fjerne). */
    suspend fun updateItem(
        id: String,
        title: String,
        description: String?,
        requiresValue: Boolean = false,
        unit: String? = null,
        minValue: Double? = null,
        maxValue: Double? = null,
        phase: ChecklistPhase = ChecklistPhase.BEFORE,
    ) = withContext(Dispatchers.Default) {
        db.checklistItemQueries.updateItem(
            id, title, description,
            if (requiresValue) 1L else 0L, unit, minValue, maxValue, phase.db,
            currentTimeMillis(),
        )
    }

    suspend fun deleteItem(id: String) = withContext(Dispatchers.Default) {
        db.checklistItemQueries.deleteItem(id, currentTimeMillis())
    }

    /** Setter ny rekkefølge: itemIds i ønsket rekkefølge får sortOrder 1, 2, 3 … */
    suspend fun reorderItems(itemIds: List<String>) = withContext(Dispatchers.Default) {
        val now = currentTimeMillis()
        db.transaction {
            itemIds.forEachIndexed { index, id ->
                db.checklistItemQueries.updateItemSortOrder(id, (index + 1).toLong(), now)
            }
        }
    }

    // ---------- Kjøringer ----------

    /**
     * Gjenbruker åpen kontroll for samme liste og ambulanse.
     *
     * Grensa er vaktlengde, ikke døgnskille: nattevakter krysser midnatt, og
     * en døgnbasert regel ville avbrutt dem midtveis. En kontroll som har stått
     * åpen lenger enn [SHIFT_TIMEOUT_MILLIS] regnes som forlatt – den bevares
     * som EXPIRED hvis noe er besvart, ellers slettes den.
     */
    suspend fun startOrResumeRun(templateId: String, ambulanceId: String): ChecklistRun =
        withContext(Dispatchers.Default) {
            val open = db.checklistRunQueries.getOpenRun(templateId, ambulanceId).executeAsOneOrNull()
            if (open != null) {
                val age = currentTimeMillis() - open.createdAt
                if (age < SHIFT_TIMEOUT_MILLIS) return@withContext open
                // En gammel kontroll uten et eneste svar er ingen dokumentasjon –
                // den slettes i stedet for å bevares som utløpt
                val answered = db.checklistRunQueries
                    .countResponsesForRunId(open.id).executeAsOne()
                if (answered == 0L) {
                    db.checklistRunQueries.deleteRun(open.id)
                } else {
                    db.checklistRunQueries.expireRun(open.id, currentTimeMillis())
                }
            }
            val id = randomId()
            db.checklistRunQueries.insertRun(id, templateId, ambulanceId, currentTimeMillis())
            db.checklistRunQueries.getRunById(id).executeAsOne()
        }

    /**
     * Starter en ny vakt selv om forrige aldri ble avsluttet.
     *
     * En glemt avslutning skal ikke sperre neste vakt fra å kontrollere bilen.
     * Den gamle kontrollen bevares som utløpt hvis noe er besvart – svarene er
     * dokumentasjon på hva som faktisk ble sjekket – og blir liggende som
     * påminnelse om at avslutningen mangler.
     */
    @Throws(CancellationException::class)
    suspend fun startNewRun(templateId: String, ambulanceId: String): ChecklistRun =
        withContext(Dispatchers.Default) {
            val open = db.checklistRunQueries.getOpenRun(templateId, ambulanceId).executeAsOneOrNull()
            if (open != null) {
                val answered = db.checklistRunQueries
                    .countResponsesForRunId(open.id).executeAsOne()
                if (answered == 0L) {
                    db.checklistRunQueries.deleteRun(open.id)
                } else {
                    db.checklistRunQueries.expireRun(open.id, currentTimeMillis())
                }
            }
            val id = randomId()
            db.checklistRunQueries.insertRun(id, templateId, ambulanceId, currentTimeMillis())
            db.checklistRunQueries.getRunById(id).executeAsOne()
        }

    fun responsesForRun(runId: String): Flow<List<ChecklistResponse>> =
        db.checklistResponseQueries.getResponsesForRun(runId)
            .asFlow().mapToList(Dispatchers.Default)

    /**
     * Lagrer svar. Lukkede kjøringer kan ikke endres.
     * Avleste verdier utenfor punktets grenseverdier flagges automatisk som MANGELFULL.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, CancellationException::class)
    suspend fun setResponse(
        runId: String,
        itemId: String,
        result: ItemResult,
        comment: String? = null,
        reading: String? = null,
    ) = withContext(Dispatchers.Default) {
        val value = reading?.let {
            requireNotNull(it.toDoubleOrNull()) { "Avlest verdi må være et tall" }
        }

        val run = db.checklistRunQueries.getRunById(runId).executeAsOneOrNull()
        check(run != null && run.status == RunStatus.IN_PROGRESS.db) {
            "Sjekklisten er lukket og kan ikke endres"
        }

        var finalResult = result
        var finalComment = comment
        if (value != null) {
            val item = db.checklistItemQueries.getItemById(itemId).executeAsOneOrNull()
            val limits = MeasurementLimits(item?.minValue, item?.maxValue)
            if (item != null && !limits.isEmpty && !limits.contains(value)) {
                finalResult = ItemResult.MANGELFULL
                if (finalComment.isNullOrBlank()) {
                    val unit = item.unit.orEmpty()
                    finalComment =
                        "Avlest $reading $unit er utenfor grense${limits.describe()}"
                }
            }
        }

        db.transaction {
            db.checklistResponseQueries.upsertResponse(
                randomId(), runId, itemId, finalResult.db, finalComment, reading, currentTimeMillis(),
            )
            // Angre eventuelle lukkinger denne kjøringen har gjort på punktet
            // (håndterer at man bytter svar frem og tilbake)
            db.checklistResponseQueries.undoResolutionsByRun(itemId, runId, currentTimeMillis())
            // Lukk tidligere åpne avvik: OK nå = RECHECK, nytt avvik = SUPERSEDED
            if (finalResult == ItemResult.JA) {
                db.checklistResponseQueries.resolveEarlierDeficiencies(
                    itemId, runId, currentTimeMillis(), reading, ResolvedVia.RECHECK.db,
                )
            } else {
                db.checklistResponseQueries.resolveEarlierDeficiencies(
                    itemId, runId, currentTimeMillis(), null, ResolvedVia.SUPERSEDED.db,
                )
            }
        }
    }

    /**
     * Signerer før-vakt-delen. Kontrollen forblir åpen – vakta er ikke over
     * før avslutningen også er signert.
     *
     * Krever at alle før-punktene er besvart, men rører ikke etter-punktene.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, CancellationException::class)
    suspend fun signBeforeShift(runId: String, userId: String) =
        withContext(Dispatchers.Default) {
            require(userId.isNotBlank()) { "Mannskaps-ID er påkrevd" }
            requireNotNull(db.userQueries.getUserById(userId).executeAsOneOrNull()) {
                "Ukjent mannskaps-ID"
            }
            val run = db.checklistRunQueries.getRunById(runId).executeAsOneOrNull()
            check(run != null && run.status == RunStatus.IN_PROGRESS.db) {
                "Kontrollen er allerede lukket"
            }
            check(run.beforeSignedAt == null) { "Før-vakt-delen er allerede signert" }

            val expected = db.checklistItemQueries
                .countItemsForTemplateTreeInPhase(run.templateId, ChecklistPhase.BEFORE.db)
                .executeAsOne()
            check(expected > 0) { "Lista har ingen punkter før vakt" }

            val answered = db.checklistResponseQueries
                .countResponsesForRunInPhase(runId, ChecklistPhase.BEFORE.db)
                .executeAsOne()
            check(answered >= expected) {
                "Alle punkter før vakt må besvares før signering ($answered av $expected)"
            }

            db.checklistRunQueries.signBeforeShift(runId, currentTimeMillis(), userId)
        }

    /**
     * Gjenåpner før-vakt-delen så mannskapet kan korrigere den underveis.
     * Mulig helt til kontrollen avsluttes; etter det er alt låst.
     */
    @Throws(IllegalStateException::class, CancellationException::class)
    suspend fun reopenBeforeShift(runId: String) = withContext(Dispatchers.Default) {
        val run = db.checklistRunQueries.getRunById(runId).executeAsOneOrNull()
        check(run != null && run.status == RunStatus.IN_PROGRESS.db) {
            "Kontrollen er lukket og kan ikke endres"
        }
        db.checklistRunQueries.reopenBeforeShift(runId, currentTimeMillis())
    }

    /**
     * Om lista har egne avslutningspunkter, og dermed skal signeres i to trinn.
     * Ukentlige og månedlige kontroller har det ikke.
     */
    fun hasAfterPhase(templateId: String): Flow<Boolean> =
        db.checklistItemQueries
            .countItemsForTemplateTreeInPhase(templateId, ChecklistPhase.AFTER.db)
            .asFlow().mapToOne(Dispatchers.Default)
            .map { it > 0 }

    /** Vakter der før-delen er signert, men avslutningen mangler. */
    fun runsAwaitingClosure(): Flow<List<GetRunsAwaitingClosure>> =
        db.checklistRunQueries.getRunsAwaitingClosure()
            .asFlow().mapToList(Dispatchers.Default)

    /**
     * Avslutter vakta med signatur på etter-vakt-delen.
     *
     * Kan signeres av et annet mannskap enn den som signerte før vakta –
     * vaktbytte, sykdom og hjemreise skjer, og alternativet ville vært at
     * kontrollen ble stående åpen.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, CancellationException::class)
    suspend fun completeRun(runId: String, userId: String, comment: String? = null) =
        withContext(Dispatchers.Default) {
            require(userId.isNotBlank()) { "Mannskaps-ID er påkrevd" }
            val run = db.checklistRunQueries.getRunById(runId).executeAsOneOrNull()
            check(run != null && run.status == RunStatus.IN_PROGRESS.db) {
                "Sjekklisten er allerede lukket"
            }
            // To-fase-signering gjelder bare lister som faktisk har
            // avslutningspunkter. Ukentlige og månedlige kontroller har det
            // ikke, og skal fortsatt kunne signeres én gang.
            val afterCount = db.checklistItemQueries
                .countItemsForTemplateTreeInPhase(run.templateId, ChecklistPhase.AFTER.db)
                .executeAsOne()
            if (afterCount > 0) {
                check(run.beforeSignedAt != null) {
                    "Før-vakt-delen må signeres før vakta kan avsluttes"
                }
            }
            val expected = db.checklistItemQueries
                .countItemsForTemplateTree(run.templateId).executeAsOne()
            // En liste uten punkter beviser ingenting – en signatur på den ville
            // sett ut som en gjennomført kontroll i arkivet
            check(expected > 0) {
                "Sjekklisten har ingen punkter og kan ikke signeres"
            }
            val answered = db.checklistResponseQueries
                .countResponsesForRun(runId).executeAsOne()
            check(answered >= expected) {
                "Alle punkter må besvares før signering ($answered av $expected)"
            }
            db.checklistRunQueries.completeRun(runId, currentTimeMillis(), userId, comment)
        }

    /** Til arkivet: alle svar i en kjøring, med punkttittel og liste/sekk-navn. */
    fun responsesWithItems(runId: String): Flow<List<GetResponsesWithItemsForRun>> =
        db.checklistResponseQueries.getResponsesWithItemsForRun(runId)
            .asFlow().mapToList(Dispatchers.Default)

    fun recentRuns(limit: Long = 20): Flow<List<GetRecentRuns>> =
        db.checklistRunQueries.getRecentRuns(limit)
            .asFlow().mapToList(Dispatchers.Default)

    /**
     * Når hver listetype sist ble fullført for ett kjøretøy, som
     * `TemplateType.db` -> tidspunkt.
     *
     * Dashbordet utledet dette ved å filtrere de 50 siste kontrollene i UI-et.
     * Det brøt sammen både når arkivet vokste forbi 50 rader og fordi det så
     * bort fra hvilken bil kontrollen gjaldt.
     */
    fun latestCompletedByType(ambulanceId: String): Flow<Map<String, Long>> =
        db.checklistRunQueries.latestCompletedRunByType(ambulanceId)
            .asFlow().mapToList(Dispatchers.Default)
            // Spørringa filtrerer bort NULL, men MAX() gjør at SQLDelight
            // likevel typer kolonnen nullable. En listetype uten fullført
            // kontroll skal mangle fra kartet, ikke ligge der med null-verdi.
            .map { rows ->
                rows.mapNotNull { row -> row.completedAt?.let { row.templateType to it } }.toMap()
            }

    // ---------- Mangler ----------

    /** Punkter som har åpne avvik fra tidligere kontroller (vises som varsel i ny kjøring). */
    fun itemIdsWithOpenDeficiencies(ambulanceId: String, excludeRunId: String): Flow<List<String>> =
        db.checklistResponseQueries.getItemIdsWithOpenDeficiencies(ambulanceId, excludeRunId)
            .asFlow().mapToList(Dispatchers.Default)

    /**
     * Åpne avvik, hvert med sporing tilbake til den første meldingen.
     *
     * [flowOn] er nødvendig, ikke pynt: mapToList flytter bare selve
     * spørringen til bakgrunn, mens operatorene nedenfor kjører der
     * strømmen samles inn – på Android er det hovedtråden. Uten den ville
     * oppslaget av kjeden per rad blitt blokkerende disk-I/O i UI-tråden.
     */
    fun openDeficiencies(): Flow<List<OpenDeficiency>> =
        db.checklistResponseQueries.getOpenDeficiencies()
            .asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toOpenDeficiency() } }
            .flowOn(Dispatchers.Default)

    private fun GetOpenDeficiencies.toOpenDeficiency(): OpenDeficiency {
        // Én spørring følger hele videreført-kjeden tilbake til den første
        // meldingen; tidligere var dette én spørring per ledd.
        val origin = db.checklistResponseQueries
            .getDeficiencyChainOrigin(itemId, checklistRunId)
            .executeAsOneOrNull()
        return OpenDeficiency(
            id = id,
            result = result,
            comment = comment,
            reading = reading,
            checkedAt = checkedAt,
            itemTitle = itemTitle,
            requiresValue = requiresValue,
            unit = unit,
            minValue = minValue,
            maxValue = maxValue,
            listName = listName,
            callSign = callSign,
            signedByName = signedByName,
            firstReportedAt = origin?.firstReportedAt,
            firstReportedByName = origin?.firstReportedByName,
        )
    }

    /**
     * Markerer et avvik som løst. Krever signatur med gyldig mannskaps-ID.
     * For målepunkter kreves ny avlest verdi innenfor punktets grenseverdier.
     * Gammel verdi (reading), ny verdi (resolvedReading), tidspunkt (resolvedAt)
     * og hvem som løste det (resolvedByUserId) bevares.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, CancellationException::class)
    suspend fun resolveDeficiency(responseId: String, userId: String, newReading: String? = null) =
        withContext(Dispatchers.Default) {
            require(userId.isNotBlank()) { "Mannskaps-ID er påkrevd" }
            requireNotNull(db.userQueries.getUserById(userId).executeAsOneOrNull()) {
                "Ukjent mannskaps-ID"
            }

            val response = db.checklistResponseQueries.getResponseById(responseId).executeAsOneOrNull()
            checkNotNull(response) { "Fant ikke avviket" }
            check(response.resolved == 0L) { "Avviket er allerede løst" }

            val item = db.checklistItemQueries.getItemById(response.itemId).executeAsOneOrNull()
            var reading: String? = null
            if (item != null && item.requiresValue == 1L) {
                val value = requireNotNull(newReading?.toDoubleOrNull()) {
                    "Ny avlest verdi er påkrevd"
                }
                val limits = MeasurementLimits(item.minValue, item.maxValue)
                check(limits.contains(value)) {
                    "Verdien er fortsatt utenfor grense${limits.describe()}"
                }
                reading = newReading
            }

            db.checklistResponseQueries.resolveDeficiency(responseId, currentTimeMillis(), reading, userId)
        }

    // ---------- Mannskap ----------

    fun users(): Flow<List<User>> =
        db.userQueries.getAllUsers()
            .asFlow().mapToList(Dispatchers.Default)

    /** id = mannskaps-ID (ikke generert). */
    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun addUser(id: String, name: String, role: String) =
        withContext(Dispatchers.Default) {
            require(id.isNotBlank()) { "Mannskaps-ID er påkrevd" }
            db.userQueries.insertUser(id, name, role, currentTimeMillis())
        }

    suspend fun deleteUser(id: String) = withContext(Dispatchers.Default) {
        db.userQueries.deleteUser(id, currentTimeMillis())
    }

    // ---------- Ambulanser ----------

    fun ambulances(): Flow<List<Ambulance>> =
        db.ambulanceQueries.getAllAmbulances()
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun addAmbulance(callSign: String, registrationNumber: String): String =
        withContext(Dispatchers.Default) {
            val id = randomId()
            db.ambulanceQueries.insertAmbulance(id, callSign, registrationNumber, currentTimeMillis())
            id
        }

    suspend fun deleteAmbulance(id: String) = withContext(Dispatchers.Default) {
        db.ambulanceQueries.deleteAmbulance(id, currentTimeMillis())
    }

    // ---------- Lenker ----------

    fun links(): Flow<List<AppLink>> =
        db.appLinkQueries.getAllLinks()
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun addLink(title: String, url: String, sortOrder: Long = 0): String =
        withContext(Dispatchers.Default) {
            val id = randomId()
            db.appLinkQueries.insertLink(id, title, url, sortOrder, currentTimeMillis())
            id
        }

    suspend fun updateLink(id: String, title: String, url: String) =
        withContext(Dispatchers.Default) {
            db.appLinkQueries.updateLink(id, title, url, currentTimeMillis())
        }

    suspend fun deleteLink(id: String) = withContext(Dispatchers.Default) {
        db.appLinkQueries.deleteLink(id, currentTimeMillis())
    }

    // ---------- Dokumenter ----------

    fun documents(): Flow<List<Document>> =
        db.documentQueries.getAllDocuments()
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun addDocument(title: String, uri: String, sortOrder: Long = 0): String =
        withContext(Dispatchers.Default) {
            val id = randomId()
            db.documentQueries.insertDocument(id, title, uri, sortOrder, currentTimeMillis())
            id
        }

    suspend fun updateDocument(id: String, title: String, uri: String) =
        withContext(Dispatchers.Default) {
            db.documentQueries.updateDocument(id, title, uri, currentTimeMillis())
        }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.Default) {
        db.documentQueries.deleteDocument(id, currentTimeMillis())
    }
}
