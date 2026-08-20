package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import database.GetRecentRuns
import database.GetResponsesWithItemsForRun
import org.example.project.Screen
import org.example.project.data.ChecklistRepository

@Composable
internal fun HistoryRunCard(run: GetRecentRuns, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(run.templateName, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                ResultBadge(statusText(run.status), statusColor(run.status))
            }
            Text(
                "${run.callSign} · ${formatMillis(run.completedAt ?: run.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            run.signedByName?.let {
                Text("Signert av $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DeviationLabel(run)
            if (run.hasUnsyncedChanges == 1L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudOff, null,
                        tint = RkOrange,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Ikke synkronisert – ligger bare på denne enheten",
                        style = MaterialTheme.typography.labelSmall,
                        color = RkOrange,
                    )
                }
            }
        }
    }
}

private fun statusText(status: String) = when (status) {
    "COMPLETED" -> "Signert"
    "EXPIRED" -> "Utløpt – ikke signert"
    else -> "Pågår"
}

private fun statusColor(status: String) = when (status) {
    "COMPLETED" -> RkGreen
    "EXPIRED" -> RkOrange
    else -> RkOrange
}

@Composable
private fun DeviationLabel(run: GetRecentRuns) {
    val total = run.deviationCount
    val resolved = run.resolvedCount
    val superseded = run.supersededCount
    val open = total - resolved - superseded

    val (text, color) = when {
        total == 0L -> "Ingen avvik" to RkGreen
        open > 0L -> {
            val parts = mutableListOf(if (open == 1L) "1 åpent avvik" else "$open åpne avvik")
            if (resolved > 0L) parts.add("$resolved løst")
            if (superseded > 0L) parts.add("$superseded videreført")
            parts.joinToString(" · ") to RkError
        }
        superseded > 0L -> {
            val text = if (resolved > 0L) "$superseded videreført · $resolved løst"
            else if (superseded == 1L) "1 avvik videreført" else "$superseded avvik videreført"
            text to RkOrange
        }
        else -> (if (total == 1L) "Avviket er løst" else "Alle $total avvik løst") to RkGreen
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    repo: ChecklistRepository,
    run: GetRecentRuns,
    onBack: () -> Unit,
) {
    val responses by remember(run.id) { repo.responsesWithItems(run.id) }.collectAsState(emptyList())
    val grouped = responses.groupBy { it.listName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(run.templateName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tilbake") }
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
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Ambulanse: ${run.callSign}")
                            Text("Dato: ${formatMillis(run.completedAt ?: run.createdAt)}")
                            run.signedByName?.let { Text("Signert av: $it") }
                            run.comment?.takeIf { it.isNotEmpty() }?.let { Text("Kommentar: $it") }
                        }
                    }
                }
                grouped.forEach { (listName, rows) ->
                    item {
                        Text(listName.uppercase(), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(rows, key = { it.id }) { response ->
                        ResponseDetailCard(response)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseDetailCard(response: GetResponsesWithItemsForRun) {
    val isResolved = response.resolved != 0L
    val isSuperseded = response.resolvedVia == "SUPERSEDED"

    val badgeText = when {
        isSuperseded -> "Videreført"
        isResolved -> "Løst"
        else -> resultLabel(response.result)
    }
    val badgeColor = when {
        isSuperseded -> RkOrange
        isResolved -> RkGreen
        else -> resultColor(response.result)
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(response.itemTitle, modifier = Modifier.weight(1f))
                ResultBadge(badgeText, badgeColor)
            }
            response.reading?.takeIf { it.isNotEmpty() }?.let {
                Text("Avlest: $it ${response.unit ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            response.comment?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val resolvedAt = response.resolvedAt
            if (isResolved && resolvedAt != null) {
                val was = "Var ${resultLabel(response.result).lowercase()}"
                val how = when (response.resolvedVia) {
                    "RECHECK" -> "OK ved senere kontroll"
                    "SUPERSEDED" -> "videreført til senere kontroll"
                    else -> response.resolvedByName?.let { "løst av $it" } ?: "løst manuelt"
                }
                var text = "$was · $how ${formatMillis(resolvedAt)}"
                response.resolvedReading?.takeIf { it.isNotEmpty() }?.let {
                    text += " · ny verdi $it ${response.unit ?: ""}"
                }
                Text(text, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
