package org.example.project.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.example.project.model.ItemResult

// Farger fra designet (DESIGN.md).
// Grønn og oransje er mørknet for å nå WCAG-kontrastkrav mot lys bakgrunn
// også i små tekststørrelser. Fargene brukes aldri alene – hvert resultat
// har også ikon og tekst, se resultIcon().
val RkRed = Color(0xFFB3000F)
val RkRedContainer = Color(0xFFD92323)
val RkError = Color(0xFFBA1A1A)
val RkErrorContainer = Color(0xFFFFDAD6)
val RkGreen = Color(0xFF1B5E20)
val RkOrange = Color(0xFF8A5200)
val RkSurface = Color(0xFFF9F9F9)

@Composable
fun RkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RkRed,
            onPrimary = Color.White,
            primaryContainer = RkRedContainer,
            onPrimaryContainer = Color.White,
            error = RkError,
            errorContainer = RkErrorContainer,
            surface = RkSurface,
            background = RkSurface,
        ),
        content = content,
    )
}

/**
 * Sant når skjermen er smal (telefon i portrett, ca. under 600 dp).
 * Settes én gang i [org.example.project.App] og leses av skjermene for å
 * velge mellom én-kolonne (telefon) og fler-kolonne (nettbrett) layout.
 */
val LocalIsCompact = staticCompositionLocalOf { false }

/** Grensen mellom telefon- og nettbrettlayout. */
val CompactWidthBreakpoint = 600.dp

/** Ytterpadding som er romslig på nettbrett og strammere på telefon. */
@Composable
fun screenPadding(): Dp = if (LocalIsCompact.current) 16.dp else 24.dp

/** Sentrert innhold med maks bredde – ser bra ut på nettbrett. */
@Composable
fun TabletContainer(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 700.dp).fillMaxWidth()) {
            content()
        }
    }
}

/**
 * Merkelapp med farge, tekst og – når det er oppgitt – ikon.
 * Fargen er alltid en forsterkning, aldri den eneste informasjonsbæreren
 * (WCAG 1.4.1): teksten står der uansett, og ikonet gir en tredje kanal
 * for dem som verken skiller fargene eller leser små bokstaver lett.
 */
@Composable
fun ResultBadge(text: String, color: Color, icon: ImageVector? = null) {
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

fun resultLabel(result: String): String =
    ItemResult.entries.firstOrNull { it.db == result }?.label ?: result

fun resultColor(result: String): Color = when (result) {
    "JA" -> RkGreen
    "ODELAGT" -> RkError
    else -> RkOrange
}

/**
 * Form som skiller resultatene fra hverandre uten farge. Hvert resultat har
 * en tydelig ulik silhuett – hake, kryss, trekant, utropstegn i sirkel – slik
 * at de kan skilles av fargeblinde og i sterkt sollys.
 */
fun resultIcon(result: String): ImageVector = when (result) {
    "JA" -> Icons.Default.CheckCircle
    "NEI" -> Icons.Default.Cancel
    "MANGELFULL" -> Icons.Default.Warning
    "ODELAGT" -> Icons.Default.Dangerous
    else -> Icons.Default.HelpOutline
}

fun formatMillis(millis: Long): String =
    SimpleDateFormat("d. MMM yyyy HH:mm", Locale("nb", "NO")).format(Date(millis))

// Tallhåndteringen lå tidligere her. Den ligger nå i sharedLogic
// (model/Numbers.kt og model/MeasurementLimits.kt), fordi den samme parsingen
// avgjør om en avlest verdi flagges som avvik – da skal den ikke finnes i én
// Kotlin-versjon og én Swift-versjon.

/** Legger på https:// hvis skjema mangler – ellers åpnes ikke lenken. */
fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.contains("://")) return trimmed
    return "https://$trimmed"
}
