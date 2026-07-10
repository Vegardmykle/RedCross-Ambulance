package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import database.AppLink
import database.Document
import kotlinx.coroutines.launch
import org.example.project.data.ChecklistRepository
import org.example.project.storage.DocumentStorage

private val recommendedApps = listOf(
    "FRR", "Lost Person Behavior", "Legevakthåndboken", "MEDeasy", "Bliksund", "Care to Translate",
)

@Composable
fun ResourcesScreen(
    repo: ChecklistRepository,
    storage: DocumentStorage,
    onRequestPdfImport: (() -> Unit)?,
) {
    val documents by repo.documents().collectAsState(emptyList())
    val links by repo.links().collectAsState(emptyList())
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var editingLink by remember { mutableStateOf<AppLink?>(null) }
    var showAddLink by remember { mutableStateOf(false) }
    var deleteDocTarget by remember { mutableStateOf<Document?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    TabletContainer {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Ressurser og skjema", style = MaterialTheme.typography.headlineMedium) }

            item {
                Text("INTERNE INSTRUKSER", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(documents, key = { it.id }) { document ->
                Card(onClick = {
                    if (!storage.openPdf(document.uri)) {
                        error = "Fant ikke PDF-en på enheten. Slett raden og legg den til på nytt."
                    }
                }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Description, null, tint = RkRed)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(document.title)
                            if (!storage.exists(document.uri)) {
                                Text("Fil mangler på enheten",
                                    style = MaterialTheme.typography.labelSmall, color = RkOrange)
                            }
                        }
                        IconButton(onClick = { deleteDocTarget = document }) {
                            Icon(Icons.Default.Delete, "Slett")
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onRequestPdfImport?.invoke() },
                    enabled = onRequestPdfImport != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Legg til PDF fra Filer")
                }
                Text("PDF-ene lagres i appen og er tilgjengelige uten dekning.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item {
                Text("REGISTRERING OG SKJEMA", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(links, key = { it.id }) { link ->
                Card(onClick = {
                    if (link.url.isEmpty()) editingLink = link
                    else uriHandler.openUri(link.url)
                }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Link, null, tint = RkRed)
                        Spacer(Modifier.width(12.dp))
                        Text(link.title, modifier = Modifier.weight(1f))
                        if (link.url.isEmpty()) {
                            Text("URL ikke satt", style = MaterialTheme.typography.labelSmall,
                                color = RkOrange)
                        }
                        IconButton(onClick = { editingLink = link }) {
                            Icon(Icons.Default.Edit, "Rediger")
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showAddLink = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Legg til lenke")
                }
            }

            item {
                Text("ANBEFALTE APPER", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(recommendedApps) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Apps, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(app)
                }
            }
            item {
                Text("Lastes ned til nettbrettet via Google Play.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    editingLink?.let { link ->
        LinkDialog(
            title = "Rediger lenke",
            initialTitle = link.title,
            initialUrl = link.url,
            onDismiss = { editingLink = null },
            onSave = { title, url ->
                scope.launch { repo.updateLink(link.id, title, url) }
                editingLink = null
            },
        )
    }

    if (showAddLink) {
        LinkDialog(
            title = "Ny lenke",
            initialTitle = "",
            initialUrl = "",
            onDismiss = { showAddLink = false },
            onSave = { title, url ->
                scope.launch { repo.addLink(title, url, links.size.toLong()) }
                showAddLink = false
            },
        )
    }

    deleteDocTarget?.let { document ->
        AlertDialog(
            onDismissRequest = { deleteDocTarget = null },
            title = { Text("Slette «${document.title}»?") },
            text = { Text("PDF-en fjernes fra appen.") },
            dismissButton = { TextButton(onClick = { deleteDocTarget = null }) { Text("Avbryt") } },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        storage.delete(document.uri)
                        repo.deleteDocument(document.id)
                    }
                    deleteDocTarget = null
                }) { Text("Slett") }
            },
        )
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Feil") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun LinkDialog(
    title: String,
    initialTitle: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var linkTitle by remember { mutableStateOf(initialTitle) }
    var linkUrl by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = linkTitle, onValueChange = { linkTitle = it },
                    label = { Text("Tittel") })
                OutlinedTextField(value = linkUrl, onValueChange = { linkUrl = it },
                    label = { Text("https://…") })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = {
            TextButton(
                enabled = linkTitle.isNotBlank(),
                onClick = { onSave(linkTitle.trim(), linkUrl.trim()) },
            ) { Text("Lagre") }
        },
    )
}
