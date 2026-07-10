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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import database.ChecklistResponse
import database.ChecklistRun
import database.ChecklistTemplate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.example.project.Screen
import org.example.project.data.ChecklistRepository
import org.example.project.model.ItemResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistRunScreen(
    repo: ChecklistRepository,
    templateType: String,
    onOpen: (Screen) -> Unit,
    onBack: (() -> Unit)?,
    onSyncRequest: (() -> Unit)? = null,
) {
    val templates by repo.topLevelTemplates().collectAsState(emptyList())
    val template = templates.firstOrNull { it.type == templateType }
    val ambulances by repo.ambulances().collectAsState(emptyList())
    val ambulanceId = ambulances.firstOrNull()?.id

    var run by remember { mutableStateOf<ChecklistRun?>(null) }
    androidx.compose.runtime.LaunchedEffect(template?.id, ambulanceId) {
        val t = template ?: return@LaunchedEffect
        val a = ambulanceId ?: return@LaunchedEffect
        run = try { repo.startOrResumeRun(t.id, a) } catch (e: Exception) { null }
    }

    val items by remember(template?.id) {
        template?.let { repo.itemsFor(it.id) } ?: flowOf(emptyList())
    }.collectAsState(emptyList())

    val bags by remember(template?.id) {
        template?.let { repo.bagsFor(it.id) } ?: flowOf(emptyList())
    }.collectAsState(emptyList())

    val responses by remember(run?.id) {
        run?.let { repo.responsesForRun(it.id) } ?: flowOf(emptyList())
    }.collectAsState(emptyList())
    val responseByItem = responses.associateBy { it.itemId }

    val earlierDeficiencyIds by remember(run?.id) {
        run?.let { repo.itemIdsWithOpenDeficiencies(it.ambulanceId, it.id) } ?: flowOf(emptyList())
    }.collectAsState(emptyList())

    var bagItems by remember { mutableStateOf(mapOf<String, List<ChecklistItem>>()) }
    val allItems = items + bags.flatMap { bagItems[it.id] ?: emptyList() }
    val answeredCount = allItems.count { responseByItem[it.id] != null }
    val allAnswered = allItems.isNotEmpty() && answeredCount == allItems.size

    val scope = rememberCoroutineScope()
    var showEditWarning by remember { mutableStateOf(false) }
    var showSignDialog by remember { mutableStateOf(false) }
    var justCompleted by remember { mutableStateOf(false) }

    fun answer(item: ChecklistItem, result: ItemResult, comment: String?, reading: String?) {
        val r = run ?: return
        scope.launch {
            try { repo.setResponse(r.id, item.id, result, comment, reading) } catch (_: Exception) {}
        }
    }

    fun sortedByAnswer(list: List<ChecklistItem>): List<ChecklistItem> =
        list.sortedWith(
            compareBy<ChecklistItem> { responseByItem[it.id]?.result == "JA" }
                .thenBy { it.sortOrder }
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(template?.name ?: "Sjekkliste") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tilbake") }
                    }
                },
                actions = {
                    if (template != null) {
                        IconButton(onClick = { showEditWarning = true }) {
                            Icon(Icons.Default.Edit, "Rediger sjekkliste")
                        }
                    }
                },
            )
        },
    ) { padding ->
        TabletContainer {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (justCompleted) {
                            Text("Sjekkliste signert og lukket", color = RkGreen)
                        }
                        LinearProgressIndicator(
                            progress = { if (allItems.isEmpty()) 0f else answeredCount.toFloat() / allItems.size },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "$answeredCount av ${allItems.size} punkter besvart",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    Text("UTSTYR", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                items(sortedByAnswer(items), key = { it.id }) { item ->
                    ChecklistItemRow(
                        item = item,
                        response = responseByItem[item.id],
                        hasEarlierDeficiency = item.id in earlierDeficiencyIds,
                        onAnswer = { result, comment, reading -> answer(item, result, comment, reading) },
                    )
                }

                items(bags, key = { it.id }) { bag ->
                    BagCard(
                        repo = repo,
                        bag = bag,
                        responseByItem = responseByItem,
                        earlierDeficiencyIds = earlierDeficiencyIds.toSet(),
                        onItemsChange = { list -> bagItems = bagItems + (bag.id to list) },
                        onAnswer = { item, result, comment, reading -> answer(item, result, comment, reading) },
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { showSignDialog = true },
                            enabled = allAnswered,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Signer og fullfør") }
                        if (!allAnswered) {
                            Text(
                                "Du må svare på alle punkter før du kan signere.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.heightIn(min = 16.dp))
                    }
                }
            }
        }
    }

    if (showEditWarning && template != null) {
        AlertDialog(
            onDismissRequest = { showEditWarning = false },
            title = { Text("Redigere sjekklisten?") },
            text = { Text("Endringer i lista gjelder for alle brukere og alle ambulanser, ikke bare deg.") },
            dismissButton = { TextButton(onClick = { showEditWarning = false }) { Text("Avbryt") } },
            confirmButton = {
                TextButton(onClick = {
                    showEditWarning = false
                    onOpen(Screen.EditTemplate(template))
                }) { Text("Fortsett") }
            },
        )
    }

    if (showSignDialog) {
        SignDialog(
            repo = repo,
            deficiencies = allItems.mapNotNull { item ->
                val response = responseByItem[item.id] ?: return@mapNotNull null
                if (response.result == "JA") null
                else Triple(item.title, resultLabel(response.result), response.comment)
            },
            onDismiss = { showSignDialog = false },
            onSign = { userId ->
                val r = run ?: return@SignDialog false
                val t = template ?: return@SignDialog false
                try {
                    repo.completeRun(r.id, userId, null)
                    justCompleted = true
                    run = repo.startOrResumeRun(t.id, r.ambulanceId)
                    onSyncRequest?.invoke() // synk i bakgrunnen; feiler stille uten dekning
                    true
                } catch (e: Exception) {
                    false
                }
            },
        )
    }
}

@Composable
fun ChecklistItemRow(
    item: ChecklistItem,
    response: ChecklistResponse?,
    hasEarlierDeficiency: Boolean,
    onAnswer: (ItemResult, String?, String?) -> Unit,
) {
    var pendingChoice by remember { mutableStateOf<ItemResult?>(null) }
    var showValueDialog by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title)
            if (hasEarlierDeficiency) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = RkOrange)
                    Spacer(Modifier.width(6.dp))
                    Text("Åpent avvik fra tidligere kontroll",
                        style = MaterialTheme.typography.labelSmall, color = RkOrange)
                }
            }
            item.description?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemResult.entries.forEach { choice ->
                    val selected = response?.result == choice.db
                    OutlinedButton(
                        onClick = {
                            if (choice == ItemResult.JA) {
                                if (item.requiresValue != 0L) showValueDialog = true
                                else onAnswer(ItemResult.JA, null, null)
                            } else {
                                pendingChoice = choice
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = if (selected) resultColor(choice.db)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(if (choice == ItemResult.MANGELFULL) "Mangel" else choice.label)
                    }
                }
            }

            response?.reading?.takeIf { it.isNotEmpty() }?.let {
                Text("Avlest: $it ${item.unit ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            response?.comment?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    pendingChoice?.let { choice ->
        CommentDialog(
            initial = response?.comment ?: "",
            onDismiss = { pendingChoice = null },
            onSave = { comment ->
                onAnswer(choice, comment.ifBlank { null }, null)
                pendingChoice = null
            },
        )
    }

    if (showValueDialog) {
        ValueDialog(
            unit = item.unit,
            initial = response?.reading ?: "",
            onDismiss = { showValueDialog = false },
            onSave = { value ->
                onAnswer(ItemResult.JA, null, value)
                showValueDialog = false
            },
        )
    }
}

@Composable
fun CommentDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kommentar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Beskriv gjerne hva som mangler eller er ødelagt.")
                OutlinedTextField(value = text, onValueChange = { text = it },
                    placeholder = { Text("Beskriv avviket") })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Lagre") } },
    )
}

@Composable
fun ValueDialog(unit: String?, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Avlest verdi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Skriv inn verdien som står på måleren (${unit ?: ""}).")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = filterNumeric(it) },
                    placeholder = { Text("F.eks. 180") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = {
            TextButton(onClick = {
                normalizeNumber(text)?.let(onSave)
            }) { Text("Lagre") }
        },
    )
}

@Composable
fun BagCard(
    repo: ChecklistRepository,
    bag: ChecklistTemplate,
    responseByItem: Map<String, ChecklistResponse>,
    earlierDeficiencyIds: Set<String>,
    onItemsChange: (List<ChecklistItem>) -> Unit,
    onAnswer: (ChecklistItem, ItemResult, String?, String?) -> Unit,
) {
    val items by remember(bag.id) { repo.itemsFor(bag.id) }.collectAsState(emptyList())
    androidx.compose.runtime.LaunchedEffect(items) { onItemsChange(items) }
    var expanded by remember { mutableStateOf(false) }

    val answered = items.count { responseByItem[it.id] != null }
    val sorted = items.sortedWith(
        compareBy<ChecklistItem> { responseByItem[it.id]?.result == "JA" }.thenBy { it.sortOrder }
    )

    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Backpack, null, tint = RkRed)
                Spacer(Modifier.width(8.dp))
                Text(bag.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "$answered/${items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (answered == items.size && items.isNotEmpty()) RkGreen
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (expanded) "Lukk" else "Åpne")
                }
            }
            if (expanded) {
                sorted.forEach { item ->
                    ChecklistItemRow(
                        item = item,
                        response = responseByItem[item.id],
                        hasEarlierDeficiency = item.id in earlierDeficiencyIds,
                        onAnswer = { result, comment, reading -> onAnswer(item, result, comment, reading) },
                    )
                }
            }
        }
    }
}

@Composable
fun SignDialog(
    repo: ChecklistRepository,
    deficiencies: List<Triple<String, String, String?>>,
    onDismiss: () -> Unit,
    onSign: suspend (String) -> Boolean,
) {
    val users by repo.users().collectAsState(emptyList())
    var crewId by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val matched = users.firstOrNull { it.id == crewId.trim() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signer sjekkliste") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (deficiencies.isEmpty()) {
                    Text("Ingen avvik registrert.", color = RkGreen)
                } else {
                    Text("Avvik som meldes:", style = MaterialTheme.typography.labelMedium)
                    deficiencies.forEach { (title, result, comment) ->
                        Text("• $title – $result" + (comment?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = crewId,
                    onValueChange = { crewId = it },
                    label = { Text("Mannskaps-ID (navn hentes automatisk)") },
                    placeholder = { Text("F.eks. 12345") },
                )
                when {
                    matched != null -> Text(matched.name, color = RkGreen)
                    crewId.isNotBlank() -> Text("Ukjent mannskaps-ID",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (failed) Text("Kunne ikke signere.", color = RkError)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = {
            TextButton(
                enabled = matched != null,
                onClick = {
                    scope.launch {
                        if (onSign(crewId.trim())) onDismiss() else failed = true
                    }
                },
            ) { Text("Fullfør kontroll") }
        },
    )
}
