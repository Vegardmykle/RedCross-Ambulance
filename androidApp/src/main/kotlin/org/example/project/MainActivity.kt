package org.example.project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.example.project.data.ChecklistRepository
import org.example.project.data.createDriver
import org.example.project.db.AppDatabase
import org.example.project.storage.AndroidDocumentStorage

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val database = AppDatabase(createDriver(applicationContext))
        val repository = ChecklistRepository(database)
        val documentStorage = AndroidDocumentStorage(applicationContext)

        setContent {
            App(
                repository = repository,
                documentStorage = documentStorage,
            )
        }
    }
}
