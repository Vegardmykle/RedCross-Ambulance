package org.example.project

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import org.example.project.ui.CompactWidthBreakpoint
import org.example.project.ui.DashboardScreen
import org.example.project.ui.EditTemplateScreen
import org.example.project.ui.LocalIsCompact
import org.example.project.ui.ResourcesScreen
import org.example.project.ui.RkRed
import org.example.project.ui.RkTheme
import org.example.project.ui.RunDetailScreen

sealed interface Screen {
    data class Run(val templateType: String) : Screen
    data class RunDetail(val run: GetRecentRuns) : Screen
    data class EditTemplate(val template: ChecklistTemplate) : Screen
}

private data class NavItem(
    val label: String,        // sidemeny (nettbrett)
    val shortLabel: String,   // bunnavigasjon (telefon) – samme som iOS
    val screenTitle: String,  // tittel i toppfeltet (telefon) – samme som iOS
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem("Dashboard", "Dashboard", "Operativ status", Icons.Default.GridView),
    NavItem("Sjekklister", "Sjekk", "Sjekkliste", Icons.Default.Checklist),
    NavItem("Arkiv & Mangler", "Mangler", "Arkiv & Mangler", Icons.Default.Archive),
    NavItem("Ressurser", "Ressurser", "Ressurser", Icons.AutoMirrored.Filled.MenuBook),
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
        val scope = rememberCoroutineScope()

        val pop: () -> Unit = { stack = stack.dropLast(1) }
        val push: (Screen) -> Unit = { stack = stack + it }

        BackHandler(enabled = stack.isNotEmpty()) { pop() }

        val ambulances by repository.ambulances().collectAsState(emptyList())
        val callSign = ambulances.firstOrNull()?.callSign

        val refresh: (() -> Unit)? = onSyncRequest?.let { sync ->
            {
                scope.launch {
                    isSyncing = true
                    sync()
                    isSyncing = false
                }
                Unit
            }
        }

        val content: @Composable () -> Unit = {
            when (val screen = stack.lastOrNull()) {
                null -> when (tab) {
                    0 -> DashboardScreen(
                        repo = repository,
                        onOpen = push,
                        onGoToArchive = { tab = 2 },
                    )
                    1 -> ChecklistRunScreen(
                        repository, "DAILY", onOpen = push, onBack = null,
                        onSyncRequest = onSyncRequest,
                    )
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

        val selectTab: (Int) -> Unit = {
            tab = it
            stack = emptyList()
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isCompact = maxWidth < CompactWidthBreakpoint

                CompositionLocalProvider(LocalIsCompact provides isCompact) {
                    if (isCompact) {
                        PhoneShell(
                            isSyncing = isSyncing,
                            onRefresh = refresh,
                            selected = tab,
                            onSelect = selectTab,
                            showChrome = stack.isEmpty(),
                            content = content,
                        )
                    } else {
                        TabletShell(
                            callSign = callSign,
                            isSyncing = isSyncing,
                            onRefresh = refresh,
                            selected = tab,
                            onSelect = selectTab,
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

/** Nettbrett/desktop: fast sidemeny til venstre. */
@Composable
private fun TabletShell(
    callSign: String?,
    isSyncing: Boolean,
    onRefresh: (() -> Unit)?,
    selected: Int,
    onSelect: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(callSign = callSign, isSyncing = isSyncing, onRefresh = onRefresh, compact = false)
        HorizontalDivider()
        Row(Modifier.fillMaxSize()) {
            Sidebar(callSign = callSign, selected = selected, onSelect = onSelect)
            VerticalDivider()
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/**
 * Telefon: speiler iOS-appen – sentrert skjermtittel øverst med sync-knapp,
 * faner nederst uten indikator-pille (kun rød farge på valgt fane).
 * Topp- og bunnlinje skjules på underskjermer, slik at innholdet får hele høyden.
 */
@Composable
private fun PhoneShell(
    isSyncing: Boolean,
    onRefresh: (() -> Unit)?,
    selected: Int,
    onSelect: (Int) -> Unit,
    showChrome: Boolean,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (showChrome) {
            // iOS-lik navigasjonslinje: sentrert tittel, sync til høyre
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                Text(
                    navItems[selected].screenTitle,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                    if (onRefresh != null) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                Modifier.height(22.dp).width(22.dp).align(Alignment.Center),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = onRefresh) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Hent data fra skyen",
                                    tint = RkRed,
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        if (showChrome) {
            HorizontalDivider()
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = index == selected,
                        onClick = { onSelect(index) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.shortLabel) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RkRed,
                            selectedTextColor = RkRed,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
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
    compact: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (compact) "RØDE KORS" else "RØDE KORS AMBULANSE",
            color = RkRed,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        if (callSign != null) {
            Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
            VerticalDivider(Modifier.height(24.dp))
            Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
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
