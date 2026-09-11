package org.example.project.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import database.ChecklistItem
import database.ChecklistResponse
import database.ChecklistRun
import database.ChecklistTemplate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.example.project.data.ChecklistRepository
import org.example.project.model.ChecklistPhase
import org.example.project.model.ItemResult
import org.example.project.model.filterNumeric
import org.example.project.model.normalizeNumber
import org.example.project.presentation.DeficiencySummary
import org.example.project.presentation.checklistRunState
import org.example.project.ui.design.ResultBadge
import org.example.project.ui.design.RkError
import org.example.project.ui.design.RkGreen
import org.example.project.ui.design.RkOrange
import org.example.project.ui.design.RkRed
import org.example.project.ui.design.TabletContainer
import org.example.project.ui.design.formatMillis
import org.example.project.ui.design.resultColor
import org.example.project.ui.design.resultIcon
import org.example.project.ui.shell.LocalAmbulanceSelection
import org.example.project.ui.shell.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistRunScreen(
    repo: ChecklistRepository,
    templateType: String,
    onOpen: (Screen) -> Unit,
    onBack: (() -> Unit)?,
) {
    val templates by repo.topLevelTemplates().collectAsState(emptyList())
    val template = templates.firstOrNull { it.type == templateType }
    val ambulances by repo.ambulances().collectAsState(emptyList())
    val selection = LocalAmbulanceSelection.current
    // Må følge valgt kjøretøy – ellers registreres kontrollen på feil bil
    val ambulanceId = ambulances.firstOrNull { it.id == selection.selectedId }?.id

    var run by remember { mutableStateOf<ChecklistRun?>(null) }
    androidx.compose.runtime.LaunchedEffect(template?.id, ambulanceId) {
        val t = template ?: return@LaunchedEffect
        val a = ambulanceId ?: return@LaunchedEffect
        run = try { repo.startOrResumeRun(t.id, a) } catch (e: Exception) { null }
    }

    // Hele treet – hovedliste og sekker. Fremdriften må kjenne sekkepunktene
    // også når sekkene ikke vises, altså etter at før-delen er signert.
    val treeItems by remember(template?.id) {
        template?.let { repo.itemsForTemplateTree(it.id) } ?: flowOf(emptyList())
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

    // All utledning skjer ett sted, delt med iOS-appen: fasedeling, tellere,
    // om delene kan signeres, og hvilke avvik som meldes.
    val state = checklistRunState(
        rootTemplateId = template?.id ?: "",
        items = treeItems,
        responses = responses,
        run = run,
    )

    val scope = rememberCoroutineScope()
    var showEditWarning by remember { mutableStateOf(false) }
    var showSignDialog by remember { mutableStateOf(false) }
    var showReopenConfirm by remember { mutableStateOf(false) }
    var justCompleted by remember { mutableStateOf(false) }

    // Hvilken del signeringsdialogen gjelder
    var signPhase by remember { mutableStateOf(ChecklistPhase.BEFORE) }

    // Navnet på den som signerte før vakta, til overskriften
    val users by repo.users().collectAsState(emptyList())
    val beforeSignedByName = run?.beforeUserId?.let { id ->
        users.firstOrNull { it.id == id }?.name
    }

    var saveError by remember { mutableStateOf<String?>(null) }

    fun answer(item: ChecklistItem, result: ItemResult, comment: String?, reading: String?) {
        val r = run ?: return
        scope.launch {
            try {
                repo.setResponse(r.id, item.id, result, comment, reading)
            } catch (e: Exception) {
                // Et svar som ikke ble lagret må mannskapet få vite om –
                // ellers tror de utstyret er kontrollert
                saveError = e.message ?: "Kunne ikke lagre svaret"
            }
        }
    }

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
                            progress = { state.overall.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${state.overall.answered} av ${state.overall.total} punkter besvart",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ---------- Del 1: før vakt ----------

                item {
                    PhaseHeader(
                        title = if (state.hasAfterPhase) "FØR VAKT" else "UTSTYR",
                        answered = state.before.answered,
                        total = state.before.total,
                        signedAt = state.beforeSignedAt,
                        signedByName = beforeSignedByName,
                        onReopen = if (state.beforeSigned) {
                            { showReopenConfirm = true }
                        } else null,
                    )
                }

                if (!state.beforeSigned) {
                    items(state.beforeItems, key = { it.id }) { item ->
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
                            onAnswer = { item, result, comment, reading -> answer(item, result, comment, reading) },
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = {
                                    signPhase = if (state.hasAfterPhase) ChecklistPhase.BEFORE
                                    else ChecklistPhase.AFTER
                                    showSignDialog = true
                                },
                                enabled = state.canSignBefore,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(if (state.hasAfterPhase) "Signer før vakt" else "Signer og fullfør")
                            }
                            if (!state.canSignBefore) {
                                Text(
                                    "Du må svare på alle punkter før du kan signere.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ---------- Del 2: etter vakt ----------

                if (state.hasAfterPhase) {
                    item {
                        PhaseHeader(
                            title = "ETTER VAKT",
                            answered = state.after.answered,
                            total = state.after.total,
                            signedAt = null,
                            signedByName = null,
                            onReopen = null,
                            hint = if (state.beforeSigned) {
                                "Fylles ut når vakta er ferdig."
                            } else {
                                "Gjøres ved vaktslutt – etter at før-kontrollen er signert."
                            },
                        )
                    }

                    items(state.afterItems, key = { it.id }) { item ->
                        ChecklistItemRow(
                            item = item,
                            response = responseByItem[item.id],
                            hasEarlierDeficiency = item.id in earlierDeficiencyIds,
                            onAnswer = { result, comment, reading -> answer(item, result, comment, reading) },
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = {
                                    signPhase = ChecklistPhase.AFTER
                                    showSignDialog = true
                                },
                                enabled = state.canComplete,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text("Signer og avslutt vakt") }
                            if (!state.beforeSigned) {
                                Text(
                                    "Før-kontrollen må signeres først.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (!state.overall.isComplete) {
                                Text(
                                    "Du må svare på alle punkter før du kan avslutte vakta.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.heightIn(min = 16.dp)) }
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

    saveError?.let { message ->
        AlertDialog(
            onDismissRequest = { saveError = null },
            title = { Text("Kunne ikke lagre") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { saveError = null }) { Text("OK") } },
        )
    }

    if (showReopenConfirm) {
        AlertDialog(
            onDismissRequest = { showReopenConfirm = false },
            title = { Text("Gjenåpne før-kontrollen?") },
            text = {
                Text(
                    "Signaturen fjernes, og du kan endre svarene. " +
                        "Før-kontrollen må signeres på nytt før vakta kan avsluttes."
                )
            },
            dismissButton = {
                TextButton(onClick = { showReopenConfirm = false }) { Text("Avbryt") }
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = run
                    showReopenConfirm = false
                    if (r != null) {
                        scope.launch {
                            try {
                                repo.reopenBeforeShift(r.id)
                                run = repo.startOrResumeRun(r.templateId, r.ambulanceId)
                            } catch (e: Exception) {
                                saveError = e.message ?: "Kunne ikke gjenåpne"
                            }
                        }
                    }
                }) { Text("Gjenåpne") }
            },
        )
    }

    if (showSignDialog) {
        val signingBefore = signPhase == ChecklistPhase.BEFORE
        SignDialog(
            repo = repo,
            title = if (signingBefore) "Signer før vakt" else "Signer og avslutt vakt",
            // Vis bare avvikene fra den delen som faktisk signeres
            deficiencies = if (signingBefore) state.beforeDeficiencies else state.allDeficiencies,
            onDismiss = { showSignDialog = false },
            onSign = { userId ->
                val r = run ?: return@SignDialog false
                val t = template ?: return@SignDialog false
                try {
                    // Lokal lagring er det som teller – signaturen er gyldig
                    // i det den ligger i SQLite, uavhengig av dekning
                    if (signingBefore) {
                        repo.signBeforeShift(r.id, userId)
                        run = repo.startOrResumeRun(t.id, r.ambulanceId)
                        return@SignDialog true
                    }
                    repo.completeRun(r.id, userId, null)
                    justCompleted = true
                    run = repo.startOrResumeRun(t.id, r.ambulanceId)
                    true
                } catch (e: Exception) {
                    saveError = e.message ?: "Kunne ikke signere"
                    false
                }
            },
        )
    }
}

/**
 * Overskrift for en fase, med egen fremdrift.
 *
 * Er fasen signert, erstattes punktene av hvem som signerte og når – slik at
 * mannskapet ser at den delen er unnagjort og ikke tror de må begynne på nytt.
 */
@Composable
private fun PhaseHeader(
    title: String,
    answered: Int,
    total: Int,
    signedAt: Long?,
    signedByName: String?,
    onReopen: (() -> Unit)?,
    hint: String? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (signedAt != null) {
                ResultBadge("SIGNERT", RkGreen, Icons.Default.CheckCircle)
            } else if (total > 0) {
                Text(
                    "$answered av $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (signedAt != null) {
            Text(
                buildString {
                    append("Signert ")
                    append(formatMillis(signedAt))
                    if (signedByName != null) append(" av $signedByName")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onReopen != null) {
                TextButton(onClick = onReopen, modifier = Modifier.heightIn(min = 44.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gjenåpne og endre")
                }
            }
        } else if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Ikon over tekst, så knappen leses både med og uten farge.
 * Teksten får bryte over to linjer i stedet for å kuttes – med forstørret
 * skrift (WCAG 1.4.4) er «Ødelagt» bredere enn knappen.
 */
@Composable
private fun AnswerLabel(icon: ImageVector, label: String, bold: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
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

            // Valgt svar markeres med fylt flate, ikon og halvfet tekst –
            // ikke bare farge (WCAG 1.4.1). Ikonene har ulik silhuett, så
            // svarene kan skilles uten å oppfatte fargeforskjellen.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemResult.entries.forEach { choice ->
                    val selected = response?.result == choice.db
                    val color = resultColor(choice.db)
                    val label = if (choice == ItemResult.MANGELFULL) "Mangel" else choice.label

                    val onClick = {
                        if (choice == ItemResult.JA) {
                            if (item.requiresValue != 0L) showValueDialog = true
                            else onAnswer(ItemResult.JA, null, null)
                        } else {
                            pendingChoice = choice
                        }
                    }
                    val shared = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics {
                            this.selected = selected
                            contentDescription =
                                if (selected) "$label, valgt" else label
                        }

                    if (selected) {
                        Button(
                            onClick = onClick,
                            modifier = shared,
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = color,
                                contentColor = Color.White,
                            ),
                        ) {
                            AnswerLabel(resultIcon(choice.db), label, bold = true)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onClick,
                            modifier = shared,
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            AnswerLabel(resultIcon(choice.db), label, bold = false)
                        }
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

/**
 * Sekk/taske som kan foldes ut. Punktene hentes her fordi kortet viser dem,
 * men fremdriften regnes ikke ut herfra – den kommer fra hele treet, slik at
 * sekkepunktene teller også når kortene er skjult.
 */
@Composable
fun BagCard(
    repo: ChecklistRepository,
    bag: ChecklistTemplate,
    responseByItem: Map<String, ChecklistResponse>,
    earlierDeficiencyIds: Set<String>,
    onAnswer: (ChecklistItem, ItemResult, String?, String?) -> Unit,
) {
    val items by remember(bag.id) { repo.itemsFor(bag.id) }.collectAsState(emptyList())
    var expanded by remember { mutableStateOf(false) }

    val answered = items.count { responseByItem[it.id] != null }
    // Fast rekkefølge – punktene skal ikke flytte seg mens mannskapet svarer
    val sorted = items.sortedBy { it.sortOrder }

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
    deficiencies: List<DeficiencySummary>,
    onDismiss: () -> Unit,
    onSign: suspend (String) -> Boolean,
    // Sier hvilken del som signeres, så mannskapet vet hva de bekrefter
    title: String = "Signer sjekkliste",
) {
    val users by repo.users().collectAsState(emptyList())
    var crewId by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val matched = users.firstOrNull { it.id == crewId.trim() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (deficiencies.isEmpty()) {
                    Text("Ingen avvik registrert.", color = RkGreen)
                } else {
                    Text("Avvik som meldes:", style = MaterialTheme.typography.labelMedium)
                    deficiencies.forEach { deficiency ->
                        Text(
                            "• ${deficiency.title} – ${deficiency.resultLabel}" +
                                (deficiency.comment?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
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
