package org.example.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.example.project.model.ItemResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Farger fra designet (DESIGN.md)
val RkRed = Color(0xFFB3000F)
val RkRedContainer = Color(0xFFD92323)
val RkError = Color(0xFFBA1A1A)
val RkErrorContainer = Color(0xFFFFDAD6)
val RkGreen = Color(0xFF2E7D32)
val RkOrange = Color(0xFFB26A00)
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

/** Sentrert innhold med maks bredde – ser bra ut på nettbrett. */
@Composable
fun TabletContainer(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 700.dp).fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun ResultBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

fun resultLabel(result: String): String =
    ItemResult.entries.firstOrNull { it.db == result }?.label ?: result

fun resultColor(result: String): Color = when (result) {
    "JA" -> RkGreen
    "ODELAGT" -> RkError
    else -> RkOrange
}

fun formatMillis(millis: Long): String =
    SimpleDateFormat("d. MMM yyyy HH:mm", Locale("nb", "NO")).format(Date(millis))

/** Kun sifre og ett desimaltegn (komma gjøres om til punktum). */
fun filterNumeric(input: String): String {
    var filtered = input.replace(",", ".").filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot >= 0) {
        filtered = filtered.substring(0, firstDot + 1) +
            filtered.substring(firstDot + 1).filter { it.isDigit() }
    }
    return filtered
}

/** «.1» → «0.1», «180.» → «180». Returnerer null hvis ikke gyldig tall. */
fun normalizeNumber(input: String): String? {
    var value = input.trim()
    if (value.endsWith(".")) value = value.dropLast(1)
    if (value.startsWith(".")) value = "0$value"
    return if (value.toDoubleOrNull() != null) value else null
}

fun fmtDouble(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Legger på https:// hvis skjema mangler – ellers åpnes ikke lenken. */
fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.contains("://")) return trimmed
    return "https://$trimmed"
}
