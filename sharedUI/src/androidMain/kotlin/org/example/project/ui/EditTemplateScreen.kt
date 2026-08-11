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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import database.ChecklistItem
import database.ChecklistTemplate
import kotlinx.coroutines.launch
import org.example.project.data.ChecklistRepository
import org.example.project.model.TemplateType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTemplateScreen(
    repo: ChecklistRepository,
    template: ChecklistTemplate,
    onBack: () -> Unit,
) {
    val bags by repo.bagsFor(template.id).collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var showNewBagDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rediger: ${template.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tilbake") }
                },
            )
        },
    ) { padding ->
        TabletContainer {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EditSection(repo, template, isBag = false)
                bags.forEach { bag ->
                    EditSection(repo, bag, isBag = true)
                }
                OutlinedButton(
                    onClick = { showNewBagDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Legg til sekk/taske")
                }
            }
        }
    }

    if (showNewBagDialog) {
        NameDialog(
            title = "Ny sekk/taske",
            placeholder = "Navn, f.eks. Barnetaske",
            initial = "",
            onDismiss = { showNewBagDialog = false },
            onSave = { name ->
                scope.launch { repo.createTemplate(name, TemplateType.BAG, template.id) }
                showNewBagDialog = false
            },
        )
    }
}

@Composable
private fun EditSection(
    repo: ChecklistRepository,
    template: ChecklistTemplate,
    isBag: Boolean,
) {
    val items by remember(template.id) { repo.itemsFor(template.id) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    var editingItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteItemTarget by remember { mutableStateOf<ChecklistItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in items.indices) return
        val reordered = items.toMutableList().apply {
            val moved = removeAt(index)
            add(target, moved)
        }
        scope.launch { repo.reorderItems(reordered.map { it.id }) }
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBag) Icon(Icons.Default.Backpack, null, tint = RkRed)
                Spacer(Modifier.width(8.dp))
                Text(template.name, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                if (isBag) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "Meny")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Endre navn") }, onClick = {
                            menuOpen = false
                            showRenameDialog = true
                        })
                        DropdownMenuItem(text = { Text("Slett sekk") }, onClick = {
                            menuOpen = false
                            showDeleteConfirm = true
                        })
                    }
                }
            }

            items.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).heightIn(min = 44.dp)
                            .padding(vertical = 4.dp),
                    ) {
                        TextButton(onClick = { editingItem = item },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Column {
                                Text(item.title, color = MaterialTheme.colorScheme.onSurface)
                                itemSubtitle(item)?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, "Flytt opp")
                    }
                    IconButton(onClick = { move(index, 1) }, enabled = index < items.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, "Flytt ned")
                    }
                    IconButton(onClick = { deleteItemTarget = item }) {
                        Icon(Icons.Default.Delete, "Slett punkt")
                    }
                }
            }

            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Legg til punkt")
            }
        }
    }

    if (showAddDialog) {
        ItemFormDialog(
            title = "Nytt punkt",
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { form ->
                scope.launch {
                    repo.addItem(template.id, form.title, form.description,
                        form.requiresValue, form.unit, form.minValue, form.maxValue)
                }
                showAddDialog = false
            },
        )
    }

    editingItem?.let { item ->
        ItemFormDialog(
            title = "Rediger punkt",
            existing = item,
            onDismiss = { editingItem = null },
            onSave = { form ->
                scope.launch {
                    repo.updateItem(item.id, form.title, form.description,
                        form.requiresValue, form.unit, form.minValue, form.maxValue)
                }
                editingItem = null
            },
        )
    }

    if (showRenameDialog) {
        NameDialog(
            title = "Endre navn",
            placeholder = "Navn",
            initial = template.name,
            onDismiss = { showRenameDialog = false },
            onSave = { name ->
                scope.launch { repo.renameTemplate(template.id, name) }
                showRenameDialog = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Slette ${template.name}?") },
            text = { Text("Sekken og alt innhold slettes.") },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Avbryt") } },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteTemplate(template.id) }
                    showDeleteConfirm = false
                }) { Text("Slett") }
            },
        )
    }

    deleteItemTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItemTarget = null },
            title = { Text("Slette «${item.title}»?") },
            dismissButton = { TextButton(onClick = { deleteItemTarget = null }) { Text("Avbryt") } },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteItem(item.id) }
                    deleteItemTarget = null
                }) { Text("Slett") }
            },
        )
    }
}

private fun itemSubtitle(item: ChecklistItem): String? {
    val parts = mutableListOf<String>()
    item.description?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    if (item.requiresValue != 0L) {
        var measure = "Måling"
        item.unit?.takeIf { it.isNotEmpty() }?.let { measure += " i $it" }
        item.minValue?.let { measure += " · min ${fmtDouble(it)}" }
        item.maxValue?.let { measure += " · maks ${fmtDouble(it)}" }
        parts.add(measure)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

data class ItemForm(
    val title: String,
    val description: String?,
    val requiresValue: Boolean,
    val unit: String?,
    val minValue: Double?,
    val maxValue: Double?,
)

@Composable
private fun ItemFormDialog(
    title: String,
    existing: ChecklistItem?,
    onDismiss: () -> Unit,
    onSave: (ItemForm) -> Unit,
) {
    var itemTitle by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var requiresValue by remember { mutableStateOf(existing?.requiresValue != 0L && existing != null) }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var minText by remember { mutableStateOf(existing?.minValue?.let(::fmtDouble) ?: "") }
    var maxText by remember { mutableStateOf(existing?.maxValue?.let(::fmtDouble) ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = itemTitle, onValueChange = { itemTitle = it },
                    label = { Text("Tittel") }, placeholder = { Text("F.eks. Brannslukker") })
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Beskrivelse (valgfritt)") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Krever avlest verdi", modifier = Modifier.weight(1f))
                    Switch(checked = requiresValue, onCheckedChange = { requiresValue = it })
                }
                if (requiresValue) {
                    OutlinedTextField(value = unit, onValueChange = { unit = it },
                        label = { Text("Enhet, f.eks. bar") })
                    OutlinedTextField(
                        value = minText, onValueChange = { minText = filterNumeric(it) },
                        label = { Text("Minste tillatte verdi") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = maxText, onValueChange = { maxText = filterNumeric(it) },
                        label = { Text("Største tillatte verdi") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Text("Verdier utenfor grensene flagges automatisk som avvik.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = {
            TextButton(
                enabled = itemTitle.isNotBlank(),
                onClick = {
                    onSave(
                        ItemForm(
                            title = itemTitle.trim(),
                            description = description.trim().ifEmpty { null },
                            requiresValue = requiresValue,
                            unit = if (requiresValue) unit.trim().ifEmpty { null } else null,
                            minValue = if (requiresValue) minText.toDoubleOrNull() else null,
                            maxValue = if (requiresValue) maxText.toDoubleOrNull() else null,
                        )
                    )
                },
            ) { Text("Lagre") }
        },
    )
}

@Composable
private fun NameDialog(
    title: String,
    placeholder: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it },
                placeholder = { Text(placeholder) })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim()) },
            ) { Text("Lagre") }
        },
    )
}
