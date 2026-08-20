package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.Screen
import org.example.project.data.ChecklistRepository
import org.example.project.model.OpenDeficiency

/**
 * Kombinert administrativ side: åpne mangler + siste sjekklister.
 * Nettbrett viser de to seksjonene side ved side, telefon under hverandre
 * i én rullbar liste.
 */
@Composable
fun ArchiveScreen(
    repo: ChecklistRepository,
    onOpen: (Screen) -> Unit,
    onSyncRequest: (() -> Unit)? = null,
) {
    val deficiencies by repo.openDeficiencies().collectAsState(emptyList())
    val runs by repo.recentRuns(50).collectAsState(emptyList())
    var resolveTarget by remember { mutableStateOf<OpenDeficiency?>(null) }
    val isCompact = LocalIsCompact.current

    Column(
        Modifier.fillMaxSize().padding(screenPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Tittel vises i toppfeltet på telefon (som iOS) – kun nettbrett trenger den her
        if (!isCompact) {
            Text(
                "Arkiv & Mangler",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Administrativ oversikt over utstyrstilstand og tidligere kontroller.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isCompact) {
            // Telefon: én rullbar liste med begge seksjonene
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DeficiencyHeader(deficiencies.size) }
                if (deficiencies.isEmpty()) {
                    item { AllClearRow() }
                } else {
                    items(deficiencies, key = { it.id }) { deficiency ->
                        DeficiencyCard(deficiency) { resolveTarget = deficiency }
                    }
                }
                item { Spacer(Modifier.fillMaxWidth().padding(top = 8.dp)) }
                item { RunsHeader() }
                if (runs.isEmpty()) {
                    item { EmptyRunsRow() }
                } else {
                    items(runs, key = { it.id }) { run ->
                        HistoryRunCard(run) { onOpen(Screen.RunDetail(run)) }
                    }
                }
            }
        } else {
            // Nettbrett: to kolonner
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeficiencyHeader(deficiencies.size)
                    if (deficiencies.isEmpty()) {
                        AllClearRow()
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(deficiencies, key = { it.id }) { deficiency ->
                                DeficiencyCard(deficiency) { resolveTarget = deficiency }
                            }
                        }
                    }
                }

                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunsHeader()
                    if (runs.isEmpty()) {
                        EmptyRunsRow()
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(runs, key = { it.id }) { run ->
                                HistoryRunCard(run) { onOpen(Screen.RunDetail(run)) }
                            }
                        }
                    }
                }
            }
        }
    }

    resolveTarget?.let { target ->
        ResolveDialog(
            repo = repo,
            deficiency = target,
            onDismiss = { resolveTarget = null },
            onResolved = onSyncRequest,
        )
    }
}

@Composable
private fun DeficiencyHeader(count: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Warning, null, tint = RkError)
        Spacer(Modifier.width(8.dp))
        Text(
            "Åpne mangler", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
        )
        ResultBadge(
            if (count == 1) "1 AKTIV" else "$count AKTIVE",
            if (count == 0) RkGreen else RkError,
        )
    }
}

@Composable
private fun RunsHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.History, null, tint = RkRed)
        Spacer(Modifier.width(8.dp))
        Text(
            "Siste sjekklister", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AllClearRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = RkGreen)
        Spacer(Modifier.width(8.dp))
        Text("Alt utstyr er meldt i orden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyRunsRow() {
    Text("Fullførte sjekklister vises her.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
