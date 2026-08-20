package org.example.project.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
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
import database.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.example.project.db.AppDatabase
import org.example.project.model.ItemResult
import org.example.project.model.OpenDeficiency
import org.example.project.model.TemplateType
import kotlin.coroutines.cancellation.CancellationException
import org.example.project.util.currentTimeMillis
import org.example.project.util.randomId
import org.example.project.util.startOfTodayMillis

/**
 * Alt UI-laget trenger for å lese og skrive sjekklister.
 * Lesing eksponeres som Flow (oppdateres automatisk ved endringer),
 * skriving er suspend-funksjoner.
 */
class ChecklistRepository(private val db: AppDatabase) {

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
    ): String = withContext(Dispatchers.Default) {
        val id = randomId()
        val next = (db.checklistItemQueries.maxSortOrderForTemplate(templateId)
            .executeAsOne().maxSort ?: 0L) + 1
        db.checklistItemQueries.insertItem(
            id, templateId, title, description,
            if (requiresValue) 1L else 0L, unit, minValue, maxValue, next,
            currentTimeMillis(),
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
    ) = withContext(Dispatchers.Default) {
        db.checklistItemQueries.updateItem(
            id, title, description,
            if (requiresValue) 1L else 0L, unit, minValue, maxValue,
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
     * Gjenbruker åpen kjøring fra samme dag for samme liste+ambulanse.
     * En usignert kjøring fra en tidligere dag bevares som EXPIRED
     * (svar og avvik beholdes), og en ny kjøring startes for dagen.
     */
    suspend fun startOrResumeRun(templateId: String, ambulanceId: String): ChecklistRun =
        withContext(Dispatchers.Default) {
            val open = db.checklistRunQueries.getOpenRun(templateId, ambulanceId).executeAsOneOrNull()
            if (open != null) {
                if (open.createdAt >= startOfTodayMillis()) return@withContext open
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
        check(run != null && run.status == "IN_PROGRESS") {
            "Sjekklisten er lukket og kan ikke endres"
        }

        var finalResult = result
        var finalComment = comment
        if (value != null) {
            val item = db.checklistItemQueries.getItemById(itemId).executeAsOneOrNull()
            val min = item?.minValue
            val max = item?.maxValue
            if ((min != null && value < min) || (max != null && value > max)) {
                finalResult = ItemResult.MANGELFULL
                if (finalComment.isNullOrBlank()) {
                    val unit = item?.unit.orEmpty()
                    finalComment = buildString {
                        append("Avlest $reading $unit er utenfor grense")
                        if (min != null) append(" (min ${fmt(min)})")
                        if (max != null) append(" (maks ${fmt(max)})")
                    }
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
                    itemId, runId, currentTimeMillis(), reading, "RECHECK",
                )
            } else {
                db.checklistResponseQueries.resolveEarlierDeficiencies(
                    itemId, runId, currentTimeMillis(), null, "SUPERSEDED",
                )
            }
        }
    }

    private fun fmt(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    /** Krever mannskaps-ID (User.id) for å lukke lista. Kan bare lukkes én gang. */
    @Throws(IllegalStateException::class, IllegalArgumentException::class, CancellationException::class)
    suspend fun completeRun(runId: String, userId: String, comment: String? = null) =
        withContext(Dispatchers.Default) {
            require(userId.isNotBlank()) { "Mannskaps-ID er påkrevd" }
            val run = db.checklistRunQueries.getRunById(runId).executeAsOneOrNull()
            check(run != null && run.status == "IN_PROGRESS") {
                "Sjekklisten er allerede lukket"
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

    // ---------- Mangler ----------

    /** Punkter som har åpne avvik fra tidligere kontroller (vises som varsel i ny kjøring). */
    fun itemIdsWithOpenDeficiencies(ambulanceId: String, excludeRunId: String): Flow<List<String>> =
        db.checklistResponseQueries.getItemIdsWithOpenDeficiencies(ambulanceId, excludeRunId)
            .asFlow().mapToList(Dispatchers.Default)

    fun openDeficiencies(): Flow<List<OpenDeficiency>> =
        db.checklistResponseQueries.getOpenDeficiencies()
            .asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toOpenDeficiency() } }

    private fun GetOpenDeficiencies.toOpenDeficiency(): OpenDeficiency {
        // Følg videreført-kjeden bakover til den opprinnelige meldingen
        var currentRunId = checklistRunId
        var earliestAt: Long? = null
        var earliestBy: String? = null
        while (true) {
            val prev = db.checklistResponseQueries
                .getSupersededPredecessor(itemId, currentRunId)
                .executeAsOneOrNull() ?: break
            earliestAt = prev.checkedAt
            earliestBy = prev.signedByName
            currentRunId = prev.checklistRunId
        }
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
            firstReportedAt = earliestAt,
            firstReportedByName = earliestBy,
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
                val min = item.minValue
                val max = item.maxValue
                check((min == null || value >= min) && (max == null || value <= max)) {
                    "Verdien er fortsatt utenfor grense" +
                        (min?.let { " (min ${fmt(it)})" } ?: "") +
                        (max?.let { " (maks ${fmt(it)})" } ?: "")
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
