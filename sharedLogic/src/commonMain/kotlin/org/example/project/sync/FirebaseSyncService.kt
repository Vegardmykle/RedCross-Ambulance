package org.example.project.sync

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.example.project.db.AppDatabase
import org.example.project.util.currentTimeMillis

/**
 * Synkronisering mot Firestore.
 * Konflikthåndtering: nyeste updatedAt vinner ved pull; push skriver hele dokumentet.
 * PDF-filer synkes ikke (krever Firebase Storage/Blaze-plan) – de forblir lokale.
 *
 * Inngangen for resten av appen er [requestSync] (avfyr og glem) og [syncAll]
 * (vent på resultatet). Push og pull er interne trinn i den rekkefølgen
 * [syncAll] bestemmer, og skal ikke kalles hver for seg.
 */
class FirebaseSyncService(private val db: AppDatabase) {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: Flow<SyncStatus> = _status

    private val firestore get() = Firebase.firestore

    /**
     * Egen scope for synkroniseringen, uavhengig av den som starter den.
     *
     * Startes synken fra en skjerm, dør den når skjermen forlates – typisk
     * rett etter signering, som er nettopp når dataene må ut til de andre
     * bilene. Da ble halve pushen borte med `JobCancellationException`.
     */
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Hindrer at flere synkroniseringer kjører oppå hverandre. */
    private val syncMutex = Mutex()

    /**
     * Utløser synkronisering og returnerer med én gang. Dette er inngangen
     * UI-et skal bruke – den kan ikke avbrytes av at en skjerm lukkes.
     */
    fun requestSync() {
        syncScope.launch { syncAll() }
    }

    /**
     * Oppstartsrutinen: hent først, seed bare hvis databasen fortsatt er tom,
     * send deretter.
     *
     * Rekkefølgen er poenget. Seedes det før pull, lager to enheter som settes
     * opp samtidig hver sin kopi av standardlistene. Kjøres på synkens egen
     * scope, som overlever at skjermen eller aktiviteten som startet den
     * forsvinner.
     */
    fun requestInitialSync(seedIfEmpty: suspend () -> Unit) {
        syncScope.launch {
            syncAll()
            try {
                seedIfEmpty()
            } catch (e: Exception) {
                // En feilet seeding skal ikke hindre at det som allerede
                // ligger lokalt blir sendt videre
                log("seeding feilet: ${e.message}")
            }
            syncAll()
        }
    }

    /** Push + pull med statusoppdatering. Kalles ved appstart og etter signering. */
    @Throws(Exception::class, kotlin.coroutines.cancellation.CancellationException::class)
    suspend fun syncAll() = syncMutex.withLock {
        _status.value = SyncStatus.Syncing
        // Leses før hentingen, ikke etter: endringer som skjer mens synken
        // pågår skal fanges av neste runde, ikke hoppes over.
        val startedAt = currentTimeMillis()
        try {
            log("start")
            ensureSignedIn()
            log("innlogget som ${Firebase.auth.currentUser?.uid ?: "ingen"}")

            // Pull FØR push. Rekkefølgen er avgjørende for at «nyeste vinner»
            // skal fungere: push skriver hele dokumentet uten å se på hva som
            // ligger der fra før, så en enhet som har vært offline ville ellers
            // kunne overskrive nyere endringer fra de andre bilene med sin egen
            // utdaterte versjon. Ved å hente først blir lokale rader som er
            // eldre enn skyen erstattet – og dermed ikke lenger usynkede.
            pullRemoteChanges()
            log("pull ferdig")

            // Push kan feile per rad uten å kaste, slik at én avvist rad ikke
            // stopper resten
            pushLocalChanges()
            log("push ferdig (${lastPushFailures} avvist)")

            // Bokføres først når hentingen faktisk lyktes. Feiler den, står
            // det gamle merket, og neste forsøk henter samme periode om igjen
            // i stedet for å hoppe over den.
            db.syncStateQueries.setSyncValue(LAST_PULL_KEY, startedAt)

            _status.value = if (lastPushFailures > 0) {
                SyncStatus.Error(
                    "$lastPushFailures endring(er) ble ikke lagret i skyen. " +
                        (lastPushError ?: "")
                )
            } else {
                SyncStatus.Idle
            }
        } catch (e: Exception) {
            // Uten denne loggen forsvinner synkfeil sporløst – kallerne bruker try?
            log("FEIL: ${e::class.simpleName}: ${e.message}")
            _status.value = if (isNetworkError(e)) {
                SyncStatus.Offline
            } else {
                SyncStatus.Error(e.message ?: "Ukjent synkroniseringsfeil")
            }
        }
    }

    /**
     * Manglende dekning er ikke en feil i denne appen – den er lokal-først.
     * Firebase gir ulike typer på iOS og Android, så vi kjenner igjen både
     * unntaksnavn og meldingstekst.
     */
    private fun isNetworkError(e: Exception): Boolean {
        val name = e::class.simpleName.orEmpty()
        val message = e.message.orEmpty().lowercase()
        return name.contains("Network", ignoreCase = true) ||
            message.contains("network") ||
            message.contains("unreachable") ||
            message.contains("timeout") ||
            message.contains("offline") ||
            message.contains("host")
    }

    internal companion object {
        const val LAST_PULL_KEY = "lastPullAt"

        /**
         * Hvor langt tilbake før forrige henting vi likevel spør om.
         *
         * updatedAt settes fra klokka på enheten som skrev raden, ikke fra
         * serveren. To enheter med litt ulik klokke kan derfor skrive en rad
         * med et tidsstempel som ligger før forrige hentetidspunkt – og uten
         * overlapp ville den raden aldri blitt hentet. Et døgn dekker all
         * realistisk klokkeavvik, og koster lite: det er bare gårsdagens
         * kontroll i tillegg.
         */
        const val PULL_OVERLAP_MILLIS: Long = 24 * 60 * 60 * 1000
    }

    /**
     * Tidspunktet inkrementell henting skal spørre fra, gitt forrige
     * hentetidspunkt.
     *
     * Null inn gir null ut, som betyr hent alt: enten er dette første synk
     * etter installasjon, eller enheten er nettopp oppgradert og vet ikke hva
     * den har gått glipp av. Da er full henting det eneste forsvarlige.
     *
     * Skilt ut som en ren funksjon fordi regelen er verdt å låse med en test –
     * en for smal grense her betyr at rader aldri hentes.
     */
    internal fun pullSinceFor(lastPull: Long?): Long? =
        lastPull?.let { (it - PULL_OVERLAP_MILLIS).coerceAtLeast(0) }

    private fun pullSince(): Long? =
        pullSinceFor(db.syncStateQueries.getSyncValue(LAST_PULL_KEY).executeAsOneOrNull())

    /**
     * Kjøringene som skal hentes. [since] = null henter alt.
     *
     * Filteret krever ingen egendefinert Firestore-indeks: enkeltfelt-indekser
     * opprettes automatisk, og her filtreres det på ett felt uten sortering.
     */
    private fun runsQuery(since: Long?) =
        firestore.collection("runs").let { collection ->
            if (since == null) collection
            else collection.where { "updatedAt" greaterThanOrEqualTo since }
        }

    private fun responsesQuery(since: Long?) =
        firestore.collection("responses").let { collection ->
            if (since == null) collection
            else collection.where { "updatedAt" greaterThanOrEqualTo since }
        }

    /** Synlig i Xcode-konsollen (iOS) og logcat (Android): filtrer på «[Sync]». */
    private fun log(message: String) {
        println("[Sync] $message")
    }

    /**
     * Anonym innlogging feiler av og til forbigående ved appstart – særlig på
     * iOS, der nøkkelringen ikke alltid er klar med én gang. Ett nytt forsøk
     * er nok i praksis, og alternativet er at mannskapet ser en feilmelding
     * for noe som løser seg selv.
     */
    private suspend fun ensureSignedIn() {
        if (Firebase.auth.currentUser != null) return
        try {
            Firebase.auth.signInAnonymously()
        } catch (first: Exception) {
            log("innlogging feilet, prøver én gang til: ${first.message}")
            kotlinx.coroutines.delay(1000)
            Firebase.auth.signInAnonymously()
        }
    }

    /**
     * Antall rader som feilet under siste push. Brukes til å rapportere
     * delvis synkronisering i stedet for å skjule den.
     */
    private class PushOutcome {
        var failed = 0
        var lastError: String? = null
    }

    /**
     * Pusher én rad. En enkelt avvist skriving (f.eks. en sikkerhetsregel som
     * blokkerer et bestemt dokument) skal ikke stoppe resten av synkroniseringen
     * – raden blir stående usynket og forsøkes på nytt neste gang.
     */
    private inline fun pushRow(
        outcome: PushOutcome,
        id: String,
        collection: String,
        write: () -> Unit,
    ) {
        try {
            write()
        } catch (e: Exception) {
            outcome.failed++
            outcome.lastError = "$collection/$id: ${e.message}"
            log("push avvist for $collection/$id: ${e.message}")
        }
    }

    private suspend fun pushLocalChanges() = withContext(Dispatchers.Default) {
        val outcome = PushOutcome()

        db.checklistTemplateQueries.getUnsyncedTemplates().executeAsList().forEach { r ->
            pushRow(outcome, r.id, "templates") {
                firestore.collection("templates").document(r.id).set(
                    TemplateDto(r.id, r.name, r.type, r.parentId, r.sortOrder, r.version, r.updatedAt, r.deleted)
                )
                db.checklistTemplateQueries.markTemplateSynced(r.id)
            }
        }
        db.checklistItemQueries.getUnsyncedItems().executeAsList().forEach { r ->
            pushRow(outcome, r.id, "items") {
                firestore.collection("items").document(r.id).set(
                    ItemDto(r.id, r.templateId, r.title, r.description, r.requiresValue,
                        r.unit, r.minValue, r.maxValue, r.sortOrder, r.phase,
                        r.updatedAt, r.deleted)
                )
                db.checklistItemQueries.markItemSynced(r.id)
            }
        }
        db.userQueries.getUnsyncedUsers().executeAsList().forEach { r ->
            pushRow(outcome, r.id, "users") {
                firestore.collection("users").document(r.id).set(
                    UserDto(r.id, r.name, r.role, r.updatedAt, r.deleted)
                )
                db.userQueries.markUserSynced(r.id)
            }
        }
        db.ambulanceQueries.getUnsyncedAmbulances().executeAsList().forEach { r ->
            pushRow(outcome, r.id, "ambulances") {
                firestore.collection("ambulances").document(r.id).set(
                    AmbulanceDto(r.id, r.callSign, r.registrationNumber, r.updatedAt, r.deleted)
                )
                db.ambulanceQueries.markAmbulanceSynced(r.id)
            }
        }
        db.appLinkQueries.getUnsyncedLinks().executeAsList().forEach { r ->
            pushRow(outcome, r.id, "links") {
                firestore.collection("links").document(r.id).set(
                    LinkDto(r.id, r.title, r.url, r.sortOrder, r.updatedAt, r.deleted)
                )
                db.appLinkQueries.markLinkSynced(r.id)
            }
        }
        db.checklistRunQueries.getUnsyncedRuns().executeAsList().forEach { r ->
            pushRow(outcome, r.id, "runs") {
                firestore.collection("runs").document(r.id).set(
                    RunDto(r.id, r.templateId, r.ambulanceId, r.userId, r.createdAt,
                        r.completedAt, r.status, r.comment,
                        r.beforeSignedAt, r.beforeUserId, r.updatedAt)
                )
                db.checklistRunQueries.markRunSynced(r.id)
            }
        }
        db.checklistResponseQueries.getUnsyncedResponses().executeAsList().forEach { r ->
            pushRow(outcome, r.id, "responses") {
                firestore.collection("responses").document(r.id).set(
                    ResponseDto(r.id, r.checklistRunId, r.itemId, r.result, r.comment, r.reading,
                        r.checkedAt, r.resolved, r.resolvedAt, r.resolvedReading, r.resolvedVia,
                        r.resolvedByRunId, r.resolvedByUserId, r.updatedAt)
                )
                db.checklistResponseQueries.markResponseSynced(r.id)
            }
        }

        lastPushFailures = outcome.failed
        lastPushError = outcome.lastError
    }

    private var lastPushFailures = 0
    private var lastPushError: String? = null

    private suspend fun pullRemoteChanges() = withContext(Dispatchers.Default) {
        val templateDocs = firestore.collection("templates").get().documents
        log("pull: fant ${templateDocs.size} maler i skyen")
        templateDocs.forEach { doc ->
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
                    dto.unit, dto.minValue, dto.maxValue, dto.sortOrder, dto.phase,
                    dto.updatedAt, dto.deleted,
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
        // Kjøringer og svar er de eneste kolleksjonene som vokser monotont.
        // Resten er små og hentes i sin helhet over.
        val since = pullSince()
        log(if (since == null) "pull: full henting av kjøringer og svar"
            else "pull: kjøringer og svar endret etter $since")

        runsQuery(since).get().documents.forEach { doc ->
            val dto = doc.data(RunDto.serializer())
            val local = db.checklistRunQueries.getRunById(dto.id).executeAsOneOrNull()
            if (local == null || dto.updatedAt > local.updatedAt) {
                db.checklistRunQueries.applyRemoteRun(
                    dto.id, dto.templateId, dto.ambulanceId, dto.userId, dto.createdAt,
                    dto.completedAt, dto.status, dto.comment,
                    dto.beforeSignedAt, dto.beforeUserId, dto.updatedAt,
                )
            }
        }
        responsesQuery(since).get().documents.forEach { doc ->
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
