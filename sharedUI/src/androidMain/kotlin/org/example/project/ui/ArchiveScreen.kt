package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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

/** Kombinert administrativ side: åpne mangler + siste sjekklister (tablet-layout). */
@Composable
fun ArchiveScreen(
    repo: ChecklistRepository,
    onOpen: (Screen) -> Unit,
) {
    val deficiencies by repo.openDeficiencies().collectAsState(emptyList())
    val runs by repo.recentRuns(50).collectAsState(emptyList())
    var resolveTarget by remember { mutableStateOf<OpenDeficiency?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Arkiv & Mangler", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text(
            "Administrativ oversikt over utstyrstilstand og tidligere kontroller.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Åpne mangler
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = RkError)
                    Spacer(Modifier.width(8.dp))
                    Text("Åpne mangler", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    ResultBadge(
                        if (deficiencies.size == 1) "1 AKTIV" else "${deficiencies.size} AKTIVE",
                        if (deficiencies.isEmpty()) RkGreen else RkError,
                    )
                }
                if (deficiencies.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = RkGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Alt utstyr er meldt i orden.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(deficiencies, key = { it.id }) { deficiency ->
                            DeficiencyCard(deficiency) { resolveTarget = deficiency }
                        }
                    }
                }
            }

            // Siste sjekklister
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = RkRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Siste sjekklister", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                if (runs.isEmpty()) {
                    Text("Fullførte sjekklister vises her.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    resolveTarget?.let { target ->
        ResolveDialog(
            repo = repo,
            deficiency = target,
            onDismiss = { resolveTarget = null },
        )
    }
}
