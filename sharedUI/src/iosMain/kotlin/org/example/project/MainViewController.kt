package org.example.project

import androidx.compose.ui.window.ComposeUIViewController
import org.example.project.data.ChecklistRepository
import org.example.project.data.createDriver
import org.example.project.db.AppDatabase
import org.example.project.storage.IosDocumentStorage
import platform.UIKit.UIViewController


private val database by lazy { AppDatabase(createDriver()) }
private val repository by lazy { ChecklistRepository(database) }
private val documentStorage by lazy { IosDocumentStorage() }

/** Entry point for iOS – kalles fra Swift. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(
        repository = repository,
        documentStorage = documentStorage,
    )
}
