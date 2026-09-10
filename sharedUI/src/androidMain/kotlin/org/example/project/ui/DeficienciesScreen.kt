package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.data.ChecklistRepository
import org.example.project.model.OpenDeficiency

// Åpne mangler vises som en seksjon i ArchiveScreen, ikke som egen skjerm.
// Kortet og løs-dialogen nedenfor er delene som faktisk brukes.

@Composable
internal fun DeficiencyCard(deficiency: OpenDeficiency, onResolve: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(deficiency.itemTitle, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                ResultBadge(
                    resultLabel(deficiency.result),
                    resultColor(deficiency.result),
                    resultIcon(deficiency.result),
                )
            }
            deficiency.comment?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${deficiency.listName} · ${deficiency.callSign} · ${formatMillis(deficiency.checkedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val firstReportedAt = deficiency.firstReportedAt
            if (firstReportedAt != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, null, tint = RkOrange)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Videreført · først meldt ${formatMillis(firstReportedAt)}" +
                            (deficiency.firstReportedByName?.let { " av $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = RkOrange,
                    )
                }
            } else if (deficiency.signedByName != null) {
                Text("Meldt av ${deficiency.signedByName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onResolve, modifier = Modifier.heightIn(min = 44.dp)) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(6.dp))
                Text("Marker som løst")
            }
        }
    }
}

@Composable
internal fun ResolveDialog(
    repo: ChecklistRepository,
    deficiency: OpenDeficiency,
    onDismiss: () -> Unit,
    onResolved: (() -> Unit)? = null,
) {
    val users by repo.users().collectAsState(emptyList())
    var crewId by remember { mutableStateOf("") }
    var valueText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val requiresValue = deficiency.requiresValue != 0L
    val matched = users.firstOrNull { it.id == crewId.trim() }

    val limits = buildList {
        deficiency.minValue?.let { add("min ${fmtDouble(it)}") }
        deficiency.maxValue?.let { add("maks ${fmtDouble(it)}") }
    }.joinToString(", ").let { if (it.isEmpty()) "" else " ($it)" }

    val canSave = matched != null && (!requiresValue || normalizeNumber(valueText) != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Løs avvik") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(deficiency.itemTitle, style = MaterialTheme.typography.titleSmall)
                if (requiresValue) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = filterNumeric(it) },
                        label = { Text("Ny avlest verdi (${deficiency.unit ?: ""})$limits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
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
                error?.let { Text(it, color = RkError) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    scope.launch {
                        val saved = try {
                            repo.resolveDeficiency(
                                deficiency.id,
                                crewId.trim(),
                                if (requiresValue) normalizeNumber(valueText) else null,
                            )
                            true
                        } catch (e: Exception) {
                            error = e.message ?: "Sjekk at verdien er innenfor grensene$limits."
                            false
                        }
                        // Synk utenfor try: den er lagret lokalt uansett, og
                        // en synkfeil skal ikke se ut som en valideringsfeil
                        if (saved) {
                            onResolved?.invoke()
                            onDismiss()
                        }
                    }
                },
            ) { Text("Marker som løst") }
        },
    )
}
