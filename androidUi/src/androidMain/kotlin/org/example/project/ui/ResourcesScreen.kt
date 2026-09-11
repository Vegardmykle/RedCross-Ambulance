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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
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

/**
 * Viser versjon og miljø nederst. Under testing kjører test- og
 * produksjonsappen side om side mot hver sin database, og en forveksling
 * kan bety at testdata havner i den ekte historikken.
 */
@Composable
private fun VersionFooter() {
    val build = rememberBuildInfo()
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (build.isDebug) {
            ResultBadge("TESTVERSJON", RkOrange)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            "Versjon ${build.versionName} (${build.versionCode})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ResourcesScreen(
    repo: ChecklistRepository,
    storage: DocumentStorage,
    onRequestPdfImport: (() -> Unit)?,
) {
    val documents by repo.documents().collectAsState(emptyList())
    val links by repo.links().collectAsState(emptyList())
    val users by repo.users().collectAsState(emptyList())
    val ambulances by repo.ambulances().collectAsState(emptyList())
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var editingLink by remember { mutableStateOf<AppLink?>(null) }
    var showAddLink by remember { mutableStateOf(false) }
    var deleteDocTarget by remember { mutableStateOf<Document?>(null) }
    var showAddUser by remember { mutableStateOf(false) }
    var deleteUserTarget by remember { mutableStateOf<database.User?>(null) }
    var showAddVehicle by remember { mutableStateOf(false) }
    var deleteVehicleTarget by remember { mutableStateOf<database.Ambulance?>(null) }
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
                    else runCatching { uriHandler.openUri(normalizeUrl(link.url)) }
                        .onFailure { error = "Kunne ikke åpne lenken. Sjekk at URL-en er riktig." }
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
                Text("ADMINISTRASJON", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                AdminExpandableCard(
                    title = "Mannskap (${users.size})",
                    icon = { Icon(Icons.Default.Person, null, tint = RkRed) },
                ) {
                    users.forEach { user ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(user.name)
                                Text("ID ${user.id} · ${user.role}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { deleteUserTarget = user }) {
                                Icon(Icons.Default.Delete, "Slett ${user.name}")
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showAddUser = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Legg til mannskap")
                    }
                }
            }
            item {
                AdminExpandableCard(
                    title = "Kjøretøy (${ambulances.size})",
                    icon = { Icon(Icons.Default.LocalHospital, null, tint = RkRed) },
                ) {
                    ambulances.forEach { ambulance ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ambulance.callSign)
                                if (ambulance.registrationNumber.isNotEmpty()) {
                                    Text(ambulance.registrationNumber,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { deleteVehicleTarget = ambulance }) {
                                Icon(Icons.Default.Delete, "Slett ${ambulance.callSign}")
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showAddVehicle = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Legg til kjøretøy")
                    }
                }
            }
            item {
                Text("Endringer gjelder alle enheter.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item { VersionFooter() }
        }
    }

    editingLink?.let { link ->
        LinkDialog(
            title = "Rediger lenke",
            initialTitle = link.title,
            initialUrl = link.url,
            onDismiss = { editingLink = null },
            onSave = { title, url ->
                scope.launch { repo.updateLink(link.id, title, normalizeUrl(url)) }
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
                scope.launch { repo.addLink(title, normalizeUrl(url), links.size.toLong()) }
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

    if (showAddUser) {
        AddUserDialog(
            onDismiss = { showAddUser = false },
            onSave = { id, name, role ->
                if (users.any { it.id == id }) {
                    error = "Mannskaps-ID $id er allerede i bruk."
                } else {
                    scope.launch {
                        try {
                            repo.addUser(id, name, role)
                        } catch (_: Exception) {
                            error = "Kunne ikke legge til mannskap."
                        }
                    }
                }
                showAddUser = false
            },
        )
    }

    deleteUserTarget?.let { user ->
        AlertDialog(
            onDismissRequest = { deleteUserTarget = null },
            title = { Text("Slette ${user.name}?") },
            text = { Text("Personen kan ikke lenger signere. Historikk beholder navnet.") },
            dismissButton = { TextButton(onClick = { deleteUserTarget = null }) { Text("Avbryt") } },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteUser(user.id) }
                    deleteUserTarget = null
                }) { Text("Slett") }
            },
        )
    }

    if (showAddVehicle) {
        AddVehicleDialog(
            onDismiss = { showAddVehicle = false },
            onSave = { callSign, reg ->
                scope.launch { repo.addAmbulance(callSign, reg) }
                showAddVehicle = false
            },
        )
    }

    deleteVehicleTarget?.let { ambulance ->
        AlertDialog(
            onDismissRequest = { deleteVehicleTarget = null },
            title = { Text("Slette ${ambulance.callSign}?") },
            text = { Text("Historikken for kjøretøyet beholdes.") },
            dismissButton = { TextButton(onClick = { deleteVehicleTarget = null }) { Text("Avbryt") } },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteAmbulance(ambulance.id) }
                    deleteVehicleTarget = null
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
private fun AdminExpandableCard(
    title: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (expanded) "Lukk" else "Åpne",
                    )
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun AddVehicleDialog(
    onDismiss: () -> Unit,
    onSave: (callSign: String, registrationNumber: String) -> Unit,
) {
    var callSign by remember { mutableStateOf("") }
    var reg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nytt kjøretøy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = callSign, onValueChange = { callSign = it },
                    label = { Text("Kallesignal") }, placeholder = { Text("F.eks. Ambulanse 2") })
                OutlinedTextField(value = reg, onValueChange = { reg = it },
                    label = { Text("Registreringsnummer (valgfritt)") })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = {
            TextButton(
                enabled = callSign.isNotBlank(),
                onClick = { onSave(callSign.trim(), reg.trim()) },
            ) { Text("Legg til") }
        },
    )
}

@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onSave: (id: String, name: String, role: String) -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Mannskap") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nytt mannskap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = id, onValueChange = { id = it },
                    label = { Text("Mannskaps-ID") }, placeholder = { Text("F.eks. 12345") })
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Fullt navn") })
                OutlinedTextField(value = role, onValueChange = { role = it },
                    label = { Text("Rolle") })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = {
            TextButton(
                enabled = id.isNotBlank() && name.isNotBlank(),
                onClick = {
                    onSave(
                        id.trim(),
                        name.trim(),
                        role.trim().ifEmpty { "Mannskap" },
                    )
                },
            ) { Text("Legg til") }
        },
    )
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
