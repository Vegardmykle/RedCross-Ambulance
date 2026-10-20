package org.example.project.sync

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app

/**
 * Firebase-prosjektet appen er bygget mot, eller null hvis den er bygget uten
 * Firebase-konfigurasjon.
 *
 * Konfigurasjonen (google-services.json / GoogleService-Info.plist) er ikke i
 * git. Den som bygger rett fra GitHub, får derfor en app uten synk – den
 * fungerer fullt ut lokalt, som en enhet som aldri har nett.
 *
 * Firebase.app kaster (Android) eller feiler på `!!` (iOS) når ingen app er
 * initialisert, så alt som går galt betyr «ikke satt opp».
 */
fun firebaseProjectId(): String? =
    runCatching { Firebase.app.options.projectId }.getOrNull()
