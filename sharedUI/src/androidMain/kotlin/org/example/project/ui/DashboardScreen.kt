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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.Screen
import org.example.project.data.ChecklistRepository
import org.example.project.util.currentTimeMillis
import org.example.project.util.startOfTodayMillis

@Composable
fun DashboardScreen(
    repo: ChecklistRepository,
    onOpen: (Screen) -> Unit,
    onGoToArchive: () -> Unit,
) {
    val ambulances by repo.ambulances().collectAsState(emptyList())
    val templates by repo.topLevelTemplates().collectAsState(emptyList())
    val links by repo.links().collectAsState(emptyList())
    val runs by repo.recentRuns(50).collectAsState(emptyList())
    val deficiencies by repo.openDeficiencies().collectAsState(emptyList())
    val uriHandler = LocalUriHandler.current

    val ambulance = ambulances.firstOrNull()
    val typeById = templates.associate { it.id to it.type }

    fun latestCompleted(type: String): Long? = runs
        .filter { it.status == "COMPLETED" && typeById[it.templateId] == type }
        .mapNotNull { it.completedAt }
        .maxOrNull()

    val dailyDone = (latestCompleted("DAILY") ?: 0L) >= startOfTodayMillis()
    val weeklyDone = (latestCompleted("WEEKLY") ?: 0L) >= currentTimeMillis() - 7L * 24 * 3600_000
    val monthlyDone = (latestCompleted("MONTHLY") ?: 0L) >= currentTimeMillis() - 30L * 24 * 3600_000

    Row(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Hovedkolonne
        Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Operativ status${ambulance?.let { " – ${it.callSign}" } ?: ""}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            OutlinedCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dagens gjøremål", style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f))
                        ResultBadge(
                            if (dailyDone) "FULLFØRT" else "IKKE PÅBEGYNT",
                            if (dailyDone) RkGreen else RkError,
                        )
                    }
                    Text(
                        "Kritisk sjekkliste for utstyrsbeholdning, medikamentkontroll og teknisk status før vaktstart.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { onOpen(Screen.Run("DAILY")) },
                        modifier = Modifier.heightIn(min = 52.dp),
                    ) {
                        Text("START DAGLIG KONTROLL", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PeriodicCard(
                    title = "Ukentlig sjekk",
                    description = "Grundig gjennomgang av utstyr og lading.",
                    done = weeklyDone,
                    icon = { Icon(Icons.Default.ViewWeek, null, tint = RkRed) },
                    buttonText = "Åpne ukesjekk",
                    onClick = { onOpen(Screen.Run("WEEKLY")) },
                    modifier = Modifier.weight(1f),
                )
                PeriodicCard(
                    title = "Månedlig sjekk",
                    description = "Beredskapsplan, utløpsdatoer og inventar.",
                    done = monthlyDone,
                    icon = { Icon(Icons.Default.CalendarMonth, null, tint = RkRed) },
                    buttonText = "Åpne månedssjekk",
                    onClick = { onOpen(Screen.Run("MONTHLY")) },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("NYLIGE HENDELSER", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (runs.isEmpty() && deficiencies.isEmpty()) {
                        Text("Ingen hendelser ennå.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    deficiencies.take(2).forEach { deficiency ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, null, tint = RkError)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Avvik: ${deficiency.itemTitle}", fontWeight = FontWeight.Medium)
                                Text(
                                    "${deficiency.listName} · ${deficiency.callSign}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    runs.take(2).forEach { run ->
                        Column {
                            Text("${run.templateName} – ${run.callSign}", fontWeight = FontWeight.Medium)
                            Text(
                                run.signedByName?.let { "Signert av $it" } ?: "Ikke signert",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(onClick = onGoToArchive, modifier = Modifier.fillMaxWidth()) {
                        Text("Vis alle logger")
                    }
                }
            }
        }

        // Høyrekolonne: hurtiglenker
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hurtiglenker", style = MaterialTheme.typography.titleLarge)
                    Text("Rask tilgang til kritiske funksjoner",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    links.forEach { link ->
                        OutlinedCard(
                            onClick = {
                                if (link.url.isNotEmpty()) {
                                    runCatching { uriHandler.openUri(normalizeUrl(link.url)) }
                                }
                            },
                            enabled = link.url.isNotEmpty(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Link, null, tint = RkRed)
                                Spacer(Modifier.width(10.dp))
                                Text(link.title, modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Medium)
                                if (link.url.isEmpty()) {
                                    Text("URL ikke satt",
                                        style = MaterialTheme.typography.labelSmall, color = RkOrange)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodicCard(
    title: String,
    description: String,
    done: Boolean,
    icon: @Composable () -> Unit,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.weight(1f))
                ResultBadge(
                    if (done) "FULLFØRT" else "VENTER",
                    if (done) RkGreen else RkOrange,
                )
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) { Text(buttonText) }
        }
    }
}
