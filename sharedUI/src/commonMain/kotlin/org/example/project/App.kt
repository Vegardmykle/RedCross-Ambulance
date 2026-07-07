package org.example.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.data.ChecklistRepository
import org.example.project.storage.DocumentStorage

@Composable
fun App(
    repository: ChecklistRepository,
    documentStorage: DocumentStorage,
) {
    MaterialTheme {
        // Midlertidig innhold for å verifisere oppkoblingen –
        // erstattes av navigasjon og skjermer i UI-steget.
        val templates by repository.topLevelTemplates().collectAsState(initial = emptyList())

        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("Sjekklister", style = MaterialTheme.typography.headlineMedium)
            if (templates.isEmpty()) {
                Text("Ingen lister ennå")
            } else {
                templates.forEach { template ->
                    Text(template.name)
                }
            }
        }
    }
}
