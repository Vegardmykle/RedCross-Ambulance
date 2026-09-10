package org.example.project.sync

/**
 * Tilstanden til synkroniseringen, som UI-et viser som banner eller ikon.
 *
 * Datamodellen som gjør synk mulig ligger i skjemaet, ikke her: alle tabeller
 * har `updatedAt` (sist endret), `synced` (0 = usynkede lokale endringer) og –
 * for redigerbare data – `deleted` (soft delete, synkes som tombstone).
 */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus

    /**
     * Ingen nettforbindelse. Skilles fra Error fordi dette er normaltilstanden
     * i en ambulanse – appen er lokal-først og fungerer fullt ut. Skal derfor
     * ikke presenteres som en feil mannskapet må gjøre noe med.
     */
    data object Offline : SyncStatus

    data class Error(val message: String) : SyncStatus
}
