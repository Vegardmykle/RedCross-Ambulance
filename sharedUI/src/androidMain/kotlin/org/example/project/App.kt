package org.example.project

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import database.ChecklistTemplate
import database.GetRecentRuns
import kotlinx.coroutines.launch
import org.example.project.data.ChecklistRepository
import org.example.project.storage.DocumentStorage
import org.example.project.ui.ArchiveScreen
import org.example.project.ui.ChecklistRunScreen
import org.example.project.ui.DashboardScreen
import org.example.project.ui.EditTemplateScreen
import org.example.project.ui.ResourcesScreen
import org.example.project.ui.RkRed
import org.example.project.ui.RkTheme
import org.example.project.ui.RunDetailScreen

sealed interface Screen {
    data class Run(val templateType: String) : Screen
    data class RunDetail(val run: GetRecentRuns) : Screen
    data class EditTemplate(val template: ChecklistTemplate) : Screen
}

private data class NavItem(val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("Dashboard", Icons.Default.GridView),
    NavItem("Sjekklister", Icons.Default.Checklist),
    NavItem("Arkiv & Mangler", Icons.Default.Archive),
    NavItem("Ressurser", Icons.AutoMirrored.Filled.MenuBook),
)

@Composable
fun App(
    repository: ChecklistRepository,
    documentStorage: DocumentStorage,
    onRequestPdfImport: (() -> Unit)? = null,
    onSyncRequest: (suspend () -> Unit)? = null,
) {
    RkTheme {
        var tab by remember { mutableIntStateOf(0) }
        var stack by remember { mutableStateOf<List<Screen>>(emptyList()) }
        var isSyncing by remember { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        val pop: () -> Unit = { stack = stack.dropLast(1) }
        val push: (Screen) -> Unit = { stack = stack + it }

        BackHandler(enabled = stack.isNotEmpty()) { pop() }

        val ambulances by repository.ambulances().collectAsState(emptyList())
        val callSign = ambulances.firstOrNull()?.callSign

        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    callSign = callSign,
                    isSyncing = isSyncing,
                    onRefresh = onSyncRequest?.let { sync ->
                        {
                            scope.launch {
                                isSyncing = true
                                sync()
                                isSyncing = false
                            }
                        }
                    },
                )
                HorizontalDivider()
                Row(Modifier.fillMaxSize()) {
                    Sidebar(
                        callSign = callSign,
                        selected = tab,
                        onSelect = {
                            tab = it
                            stack = emptyList()
                        },
                    )
                    VerticalDivider()
                    Box(Modifier.fillMaxSize()) {
                        when (val screen = stack.lastOrNull()) {
                            null -> when (tab) {
                                0 -> DashboardScreen(
                                    repo = repository,
                                    onOpen = push,
                                    onGoToArchive = { tab = 2 },
                                )
                                1 -> ChecklistRunScreen(repository, "DAILY", onOpen = push, onBack = null, onSyncRequest = onSyncRequest)
                                2 -> ArchiveScreen(repository, onOpen = push, onSyncRequest = onSyncRequest)
                                else -> ResourcesScreen(repository, documentStorage, onRequestPdfImport)
                            }
                            is Screen.Run -> ChecklistRunScreen(
                                repository, screen.templateType, onOpen = push, onBack = pop,
                                onSyncRequest = onSyncRequest,
                            )
                            is Screen.RunDetail -> RunDetailScreen(repository, screen.run, onBack = pop)
                            is Screen.EditTemplate -> EditTemplateScreen(repository, screen.template, onBack = pop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    callSign: String?,
    isSyncing: Boolean,
    onRefresh: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "RØDE KORS AMBULANSE",
            color = RkRed,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        if (callSign != null) {
            Spacer(Modifier.width(16.dp))
            VerticalDivider(Modifier.height(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                callSign.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onRefresh != null) {
            if (isSyncing) {
                CircularProgressIndicator(Modifier.height(24.dp).width(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Hent data fra skyen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    callSign: String?,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier.width(240.dp).fillMaxHeight().padding(12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocalHospital, null, tint = RkRed)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Operativ enhet", fontWeight = FontWeight.Medium)
                Text(
                    callSign ?: "–",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        navItems.forEachIndexed { index, item ->
            val isSelected = index == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        if (isSelected) RkRed else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    item.icon, null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    item.label,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}
