package org.example.project.sync

import kotlinx.coroutines.flow.Flow

/**
 * Kontrakt for synkronisering mot backend. Appen kjenner bare dette
 * interfacet – implementasjonen kan være Firebase (Firestore/Storage)
 * eller et internt Røde Kors-API, og byttes uten å endre appen.
 *
 * Datamodellen er forberedt: alle tabeller har `updatedAt` (sist endret),
 * `synced` (0 = usynkede lokale endringer) og – for redigerbare data –
 * `deleted` (soft delete, synkes som tombstone).
 */
interface SyncService {

    /** Dytter lokale, usynkede endringer til backend og setter synced = 1. */
    suspend fun pushLocalChanges()

    /** Henter endringer fra backend (maler, brukere, lenker, PDF-metadata). */
    suspend fun pullRemoteChanges()

    /** Status til et lite sync-ikon i UI. */
    val status: Flow<SyncStatus>
}

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Error(val message: String) : SyncStatus
}
