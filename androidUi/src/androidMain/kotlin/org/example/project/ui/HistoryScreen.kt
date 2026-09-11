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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
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
import org.example.project.model.RunStatus
import org.example.project.presentation.DeviationOutcome
import org.example.project.presentation.ResponseOutcome
import org.example.project.presentation.deviationSummary
import org.example.project.presentation.responseBadge
import org.example.project.presentation.resolutionText

@Composable
internal fun HistoryRunCard(run: GetRecentRuns, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(run.templateName, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                ResultBadge(statusText(run.status), statusColor(run.status), statusIcon(run.status))
            }
            Text(
                "${run.callSign} · ${formatMillis(run.completedAt ?: run.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Er lista signert i to trinn, må begge signaturene fram – ellers
            // forsvinner hvem som faktisk kontrollerte bilen før vakta
            run.beforeSignedByName?.let {
                Text("Før vakt: $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            run.signedByName?.let {
                val label = if (run.beforeSignedByName != null) "Etter vakt" else "Signert av"
                Text("$label: $it", style = MaterialTheme.typography.labelSmall,
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

// En status appen ikke kjenner kan komme fra en nyere versjon via synk.
// Den vises som ukjent i stedet for å bli presentert som «Pågår», som ville
// antydet at kontrollen kan tas opp igjen.
private fun statusText(status: String) =
    RunStatus.fromDb(status)?.label ?: "Ukjent status"

/** Ikon i tillegg til farge, så statusen kan leses uten fargesyn. */
private fun statusIcon(status: String) = when (RunStatus.fromDb(status)) {
    RunStatus.COMPLETED -> Icons.Default.CheckCircle
    RunStatus.EXPIRED -> Icons.Default.Schedule
    else -> Icons.Default.HourglassEmpty
}

private fun statusColor(status: String) = when (RunStatus.fromDb(status)) {
    RunStatus.COMPLETED -> RkGreen
    else -> RkOrange
}

@Composable
private fun DeviationLabel(run: GetRecentRuns) {
    val summary = deviationSummary(run.deviationCount, run.resolvedCount, run.supersededCount)
    val color = when (summary.outcome) {
        DeviationOutcome.OPEN -> RkError
        DeviationOutcome.CARRIED_OVER -> RkOrange
        DeviationOutcome.NO_DEVIATIONS, DeviationOutcome.RESOLVED -> RkGreen
    }
    Text(summary.text, style = MaterialTheme.typography.labelMedium, color = color)
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
                            // Begge signaturene med tidspunkt, så det er sporbart
                            // hvem som kontrollerte bilen når
                            run.beforeSignedAt?.let { signedAt ->
                                Text(
                                    "Før vakt: ${run.beforeSignedByName ?: "ukjent"}" +
                                        " · ${formatMillis(signedAt)}"
                                )
                            }
                            run.signedByName?.let {
                                val label = if (run.beforeSignedAt != null) "Etter vakt" else "Signert av"
                                val time = run.completedAt?.let { t -> " · ${formatMillis(t)}" } ?: ""
                                Text("$label: $it$time")
                            }
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
    val badge = responseBadge(response)
    val badgeColor = when (badge.outcome) {
        ResponseOutcome.CARRIED_OVER -> RkOrange
        ResponseOutcome.RESOLVED, ResponseOutcome.OK -> RkGreen
        ResponseOutcome.DEVIATION -> resultColor(response.result)
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(response.itemTitle, modifier = Modifier.weight(1f))
                ResultBadge(badge.text, badgeColor, resultIcon(response.result))
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
            if (response.resolved != 0L && resolvedAt != null) {
                Text(
                    resolutionText(response, formatMillis(resolvedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
