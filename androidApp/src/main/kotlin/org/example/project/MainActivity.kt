package org.example.project

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.ChecklistRepository
import org.example.project.data.DatabaseSeeder
import org.example.project.data.createDriver
import org.example.project.db.AppDatabase
import org.example.project.storage.AndroidDocumentStorage
import org.example.project.sync.FirebaseSyncService

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase(createDriver(applicationContext)) }
    private val repository by lazy { ChecklistRepository(database) }
    private val documentStorage by lazy { AndroidDocumentStorage(applicationContext) }
    private val syncService by lazy { FirebaseSyncService(database) }

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            // Pull først så vi ikke seeder duplikater av data som finnes i skyen,
            // seed bare hvis databasen fortsatt er tom, push deretter.
            syncService.syncAll()
            DatabaseSeeder(database).seedIfEmpty()
            syncService.syncAll()
        }

        setContent {
            App(
                repository = repository,
                documentStorage = documentStorage,
                onRequestPdfImport = { pickPdf.launch(arrayOf("application/pdf")) },
                onSyncRequest = {
                    lifecycleScope.launch { syncService.syncAll() }
                },
            )
        }
    }

    private fun importPdf(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = queryDisplayName(uri) ?: "dokument.pdf"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val path = documentStorage.save(fileName, bytes)
                val title = fileName.removeSuffix(".pdf")
                withContext(Dispatchers.Default) {
                    repository.addDocument(title, path)
                }
            } catch (_: Exception) {
                // Import feilet – stille; brukeren kan prøve igjen
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
}
