package org.example.project.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

class AndroidDocumentStorage(private val context: Context) : DocumentStorage {

    private val dir: File
        get() = File(context.filesDir, "documents").apply { mkdirs() }

    override fun save(fileName: String, bytes: ByteArray): String {
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun delete(path: String) {
        File(path).delete()
    }

    override fun openPdf(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
