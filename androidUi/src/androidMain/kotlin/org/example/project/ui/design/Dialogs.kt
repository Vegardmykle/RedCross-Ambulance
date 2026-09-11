package org.example.project.ui.design

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Bekreftelse før noe fjernes.
 *
 * Lå tidligere utskrevet fem steder – sletting av PDF, mannskap, kjøretøy,
 * sekk og punkt. Formen var lik hver gang, men [text] var det ikke: den sier
 * hva som faktisk skjer med historikken, og det er forskjellig fra sted til
 * sted. Derfor er teksten en parameter og ikke noe komponenten finner på selv.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Slett",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
    )
}

/**
 * Melding mannskapet bare skal lese og lukke – typisk en feil som allerede
 * har skjedd, der det ikke finnes noe valg å ta.
 */
@Composable
fun MessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
