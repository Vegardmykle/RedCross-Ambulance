package org.example.project.sync

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.example.project.db.AppDatabase

/**
 * Firestore-implementasjon av SyncService.
 * Konflikthåndtering: nyeste updatedAt vinner ved pull; push skriver hele dokumentet.
 * PDF-filer synkes ikke (krever Firebase Storage/Blaze-plan) – de forblir lokale.
 */
class FirebaseSyncService(private val db: AppDatabase) : SyncService {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: Flow<SyncStatus> = _status

    private val firestore get() = Firebase.firestore

    /** Push + pull med statusoppdatering. Kalles ved appstart og etter signering. */
    suspend fun syncAll() {
        _status.value = SyncStatus.Syncing
        try {
            ensureSignedIn()
            pushLocalChanges()
            pullRemoteChanges()
            _status.value = SyncStatus.Idle
        } catch (e: Exception) {
            _status.value = SyncStatus.Error(e.message ?: "Ukjent synkroniseringsfeil")
        }
    }

    private suspend fun ensureSignedIn() {
        if (Firebase.auth.currentUser == null) {
            Firebase.auth.signInAnonymously()
        }
    }

    override suspend fun pushLocalChanges() = withContext(Dispatchers.Default) {
        db.checklistTemplateQueries.getUnsyncedTemplates().executeAsList().forEach { r ->
            firestore.collection("templates").document(r.id).set(
                TemplateDto(r.id, r.name, r.type, r.parentId, r.sortOrder, r.version, r.updatedAt, r.deleted)
            )
            db.checklistTemplateQueries.markTemplateSynced(r.id)
        }
        db.checklistItemQueries.getUnsyncedItems().executeAsList().forEach { r ->
            firestore.collection("items").document(r.id).set(
                ItemDto(r.id, r.templateId, r.title, r.description, r.requiresValue,
                    r.unit, r.minValue, r.maxValue, r.sortOrder, r.updatedAt, r.deleted)
            )
            db.checklistItemQueries.markItemSynced(r.id)
        }
        db.userQueries.getUnsyncedUsers().executeAsList().forEach { r ->
            firestore.collection("users").document(r.id).set(
                UserDto(r.id, r.name, r.role, r.updatedAt, r.deleted)
            )
            db.userQueries.markUserSynced(r.id)
        }
        db.ambulanceQueries.getUnsyncedAmbulances().executeAsList().forEach { r ->
            firestore.collection("ambulances").document(r.id).set(
                AmbulanceDto(r.id, r.callSign, r.registrationNumber, r.updatedAt, r.deleted)
            )
            db.ambulanceQueries.markAmbulanceSynced(r.id)
        }
        db.appLinkQueries.getUnsyncedLinks().executeAsList().forEach { r ->
            firestore.collection("links").document(r.id).set(
                LinkDto(r.id, r.title, r.url, r.sortOrder, r.updatedAt, r.deleted)
            )
            db.appLinkQueries.markLinkSynced(r.id)
        }
        db.checklistRunQueries.getUnsyncedRuns().executeAsList().forEach { r ->
            firestore.collection("runs").document(r.id).set(
                RunDto(r.id, r.templateId, r.ambulanceId, r.userId, r.createdAt,
                    r.completedAt, r.status, r.comment, r.updatedAt)
            )
            db.checklistRunQueries.markRunSynced(r.id)
        }
        db.checklistResponseQueries.getUnsyncedResponses().executeAsList().forEach { r ->
            firestore.collection("responses").document(r.id).set(
                ResponseDto(r.id, r.checklistRunId, r.itemId, r.result, r.comment, r.reading,
                    r.checkedAt, r.resolved, r.resolvedAt, r.resolvedReading, r.resolvedVia,
                    r.resolvedByRunId, r.resolvedByUserId, r.updatedAt)
            )
            db.checklistResponseQueries.markResponseSynced(r.id)
        }
    }

    override suspend fun pullRemoteChanges() = withContext(Dispatchers.Default) {
        firestore.collection("templates").get().documents.forEach { doc ->
            val dto = doc.data(TemplateDto.serializer())
            val local = db.checklistTemplateQueries.getTemplateById(dto.id).executeAsOneOrNull()
            if (local == null || dto.updatedAt > local.updatedAt) {
                db.checklistTemplateQueries.applyRemoteTemplate(
                    dto.id, dto.name, dto.type, dto.parentId, dto.sortOrder,
                    dto.version, dto.updatedAt, dto.deleted,
                )
            }
        }
        firestore.collection("items").get().documents.forEach { doc ->
            val dto = doc.data(ItemDto.serializer())
            val local = db.checklistItemQueries.getItemById(dto.id).executeAsOneOrNull()
            if (local == null || dto.updatedAt > local.updatedAt) {
                db.checklistItemQueries.applyRemoteItem(
                    dto.id, dto.templateId, dto.title, dto.description, dto.requiresValue,
                    dto.unit, dto.minValue, dto.maxValue, dto.sortOrder, dto.updatedAt, dto.deleted,
                )
            }
        }
        firestore.collection("users").get().documents.forEach { doc ->
            val dto = doc.data(UserDto.serializer())
            val local = db.userQueries.getUserByIdAny(dto.id).executeAsOneOrNull()
            if (local == null || dto.updatedAt > local.updatedAt) {
                db.userQueries.applyRemoteUser(dto.id, dto.name, dto.role, dto.updatedAt, dto.deleted)
            }
        }
        firestore.collection("ambulances").get().documents.forEach { doc ->
            val dto = doc.data(AmbulanceDto.serializer())
            val local = db.ambulanceQueries.getAmbulanceById(dto.id).executeAsOneOrNull()
            if (local == null || dto.updatedAt > local.updatedAt) {
                db.ambulanceQueries.applyRemoteAmbulance(
                    dto.id, dto.callSign, dto.registrationNumber, dto.updatedAt, dto.deleted,
                )
            }
        }
        firestore.collection("links").get().documents.forEach { doc ->
            val dto = doc.data(LinkDto.serializer())
            val local = db.appLinkQueries.getLinkById(dto.id).executeAsOneOrNull()
            if (local == null || dto.updatedAt > local.updatedAt) {
                db.appLinkQueries.applyRemoteLink(
                    dto.id, dto.title, dto.url, dto.sortOrder, dto.updatedAt, dto.deleted,
                )
            }
        }
        firestore.collection("runs").get().documents.forEach { doc ->
            val dto = doc.data(RunDto.serializer())
            val local = db.checklistRunQueries.getRunById(dto.id).executeAsOneOrNull()
            if (local == null || dto.updatedAt > local.updatedAt) {
                db.checklistRunQueries.applyRemoteRun(
                    dto.id, dto.templateId, dto.ambulanceId, dto.userId, dto.createdAt,
                    dto.completedAt, dto.status, dto.comment, dto.updatedAt,
                )
            }
        }
        firestore.collection("responses").get().documents.forEach { doc ->
            val dto = doc.data(ResponseDto.serializer())
            val local = db.checklistResponseQueries.getResponseById(dto.id).executeAsOneOrNull()
            if (local == null || dto.updatedAt > local.updatedAt) {
                db.checklistResponseQueries.applyRemoteResponse(
                    dto.id, dto.checklistRunId, dto.itemId, dto.result, dto.comment, dto.reading,
                    dto.checkedAt, dto.resolved, dto.resolvedAt, dto.resolvedReading,
                    dto.resolvedVia, dto.resolvedByRunId, dto.resolvedByUserId, dto.updatedAt,
                )
            }
        }
    }
}
