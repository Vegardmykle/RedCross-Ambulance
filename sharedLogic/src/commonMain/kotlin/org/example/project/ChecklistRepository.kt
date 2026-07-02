package org.example.project

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.example.project.db.AppDatabase

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
        db.checklistTemplateQueries.insertTemplate(id, name, type.db, parentId, 0)
        id
    }

    suspend fun renameTemplate(id: String, name: String) =
        withContext(Dispatchers.Default) {
            db.checklistTemplateQueries.updateTemplateName(id, name)
        }

    /** Sletter mal, dens punkter og eventuelle sekker med innhold. */
    suspend fun deleteTemplate(id: String) = withContext(Dispatchers.Default) {
        db.transaction {
            db.checklistTemplateQueries.getBagsForTemplate(id).executeAsList().forEach { bag ->
                db.checklistItemQueries.deleteItemsForTemplate(bag.id)
                db.checklistTemplateQueries.deleteTemplate(bag.id)
            }
            db.checklistItemQueries.deleteItemsForTemplate(id)
            db.checklistTemplateQueries.deleteTemplate(id)
        }
    }

    suspend fun addItem(
        templateId: String,
        title: String,
        description: String? = null,
    ): String = withContext(Dispatchers.Default) {
        val id = randomId()
        val next = (db.checklistItemQueries.maxSortOrderForTemplate(templateId)
            .executeAsOne().maxSort ?: 0L) + 1
        db.checklistItemQueries.insertItem(id, templateId, title, description, next)
        id
    }

    suspend fun updateItem(id: String, title: String, description: String?) =
        withContext(Dispatchers.Default) {
            db.checklistItemQueries.updateItem(id, title, description)
        }

    suspend fun deleteItem(id: String) = withContext(Dispatchers.Default) {
        db.checklistItemQueries.deleteItem(id)
    }

    // ---------- Kjøringer ----------

    /** Gjenbruker åpen kjøring for samme liste+ambulanse, ellers ny. */
    suspend fun startOrResumeRun(templateId: String, ambulanceId: String): ChecklistRun =
        withContext(Dispatchers.Default) {
            db.checklistRunQueries.getOpenRun(templateId, ambulanceId).executeAsOneOrNull()
                ?: run {
                    val id = randomId()
                    db.checklistRunQueries.insertRun(id, templateId, ambulanceId, currentTimeMillis())
                    db.checklistRunQueries.getRunById(id).executeAsOne()
                }
        }

    fun responsesForRun(runId: String): Flow<List<ChecklistResponse>> =
        db.checklistResponseQueries.getResponsesForRun(runId)
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun setResponse(
        runId: String,
        itemId: String,
        result: ItemResult,
        comment: String? = null,
    ) = withContext(Dispatchers.Default) {
        db.checklistResponseQueries.upsertResponse(
            randomId(), runId, itemId, result.db, comment, currentTimeMillis(),
        )
    }

    /** Krever signatur (mannskaps-ID/navn) for å lukke lista. */
    suspend fun completeRun(runId: String, signature: String, comment: String? = null) =
        withContext(Dispatchers.Default) {
            require(signature.isNotBlank()) { "Signatur er påkrevd" }
            db.checklistRunQueries.completeRun(runId, currentTimeMillis(), signature, comment)
        }

    fun recentRuns(limit: Long = 20): Flow<List<GetRecentRuns>> =
        db.checklistRunQueries.getRecentRuns(limit)
            .asFlow().mapToList(Dispatchers.Default)

    // ---------- Mangler ----------

    fun openDeficiencies(): Flow<List<GetOpenDeficiencies>> =
        db.checklistResponseQueries.getOpenDeficiencies()
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun resolveDeficiency(responseId: String) =
        withContext(Dispatchers.Default) {
            db.checklistResponseQueries.resolveDeficiency(responseId)
        }

    // ---------- Ambulanser ----------

    fun ambulances(): Flow<List<Ambulance>> =
        db.ambulanceQueries.getAllAmbulances()
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun addAmbulance(callSign: String, registrationNumber: String): String =
        withContext(Dispatchers.Default) {
            val id = randomId()
            db.ambulanceQueries.insertAmbulance(id, callSign, registrationNumber)
            id
        }

    suspend fun deleteAmbulance(id: String) = withContext(Dispatchers.Default) {
        db.ambulanceQueries.deleteAmbulance(id)
    }

    // ---------- Lenker ----------

    fun links(): Flow<List<AppLink>> =
        db.appLinkQueries.getAllLinks()
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun addLink(title: String, url: String, sortOrder: Long = 0): String =
        withContext(Dispatchers.Default) {
            val id = randomId()
            db.appLinkQueries.insertLink(id, title, url, sortOrder)
            id
        }

    suspend fun updateLink(id: String, title: String, url: String) =
        withContext(Dispatchers.Default) {
            db.appLinkQueries.updateLink(id, title, url)
        }

    suspend fun deleteLink(id: String) = withContext(Dispatchers.Default) {
        db.appLinkQueries.deleteLink(id)
    }

    // ---------- Dokumenter ----------

    fun documents(): Flow<List<Document>> =
        db.documentQueries.getAllDocuments()
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun addDocument(title: String, uri: String, sortOrder: Long = 0): String =
        withContext(Dispatchers.Default) {
            val id = randomId()
            db.documentQueries.insertDocument(id, title, uri, sortOrder)
            id
        }

    suspend fun updateDocument(id: String, title: String, uri: String) =
        withContext(Dispatchers.Default) {
            db.documentQueries.updateDocument(id, title, uri)
        }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.Default) {
        db.documentQueries.deleteDocument(id)
    }
}
