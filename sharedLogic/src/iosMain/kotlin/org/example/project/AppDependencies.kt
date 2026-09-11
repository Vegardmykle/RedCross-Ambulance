package org.example.project

import org.example.project.data.ChecklistRepository
import org.example.project.data.DatabaseSeeder
import org.example.project.data.createDriver
import org.example.project.db.AppDatabase
import org.example.project.storage.IosDocumentStorage
import org.example.project.sync.FirebaseSyncService

/**
 * Ett felles inngangspunkt for Swift-koden.
 * Alt opprettes lazy og deles av hele appen:
 * `AppDependencies.shared.repository` osv.
 */
object AppDependencies {
    val database: AppDatabase by lazy { AppDatabase(createDriver()) }
    val documentStorage: IosDocumentStorage by lazy { IosDocumentStorage() }
    val seeder: DatabaseSeeder by lazy { DatabaseSeeder(database) }
    val syncService: FirebaseSyncService by lazy { FirebaseSyncService(database) }

    /**
     * Repositoryet varsler synken selv når noe delbart er endret, så ingen
     * skjerm kan glemme det. syncService opprettes først, så det er ingen
     * sirkulær avhengighet – lambdaen slår den opp når den kalles.
     */
    val repository: ChecklistRepository by lazy {
        ChecklistRepository(database) { syncService.requestSync() }
    }
}
