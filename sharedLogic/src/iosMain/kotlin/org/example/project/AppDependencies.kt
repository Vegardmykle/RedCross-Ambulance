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
    val repository: ChecklistRepository by lazy { ChecklistRepository(database) }
    val documentStorage: IosDocumentStorage by lazy { IosDocumentStorage() }
    val seeder: DatabaseSeeder by lazy { DatabaseSeeder(database) }
    val syncService: FirebaseSyncService by lazy { FirebaseSyncService(database) }
}
