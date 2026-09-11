package org.example.project.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import database.Ambulance

/**
 * Hvilken ambulanse appen jobber med. Valget lagres på enheten, slik at
 * nettbrettet i en bil husker sitt eget kjøretøy mellom vakter.
 *
 * Uten dette brukte alle skjermene den første ambulansen i lista, og det var
 * umulig å velge en annen når gruppa hadde flere kjøretøy.
 */
data class AmbulanceSelection(
    val selectedId: String?,
    val select: (String) -> Unit,
)

val LocalAmbulanceSelection = staticCompositionLocalOf {
    AmbulanceSelection(selectedId = null, select = {})
}

private const val PREFS = "rk_ambulanse"
private const val KEY_SELECTED = "selectedAmbulanceId"

@Composable
fun rememberAmbulanceSelection(available: List<Ambulance>): AmbulanceSelection {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    var stored by remember { mutableStateOf(prefs.getString(KEY_SELECTED, null)) }

    // Faller tilbake til første kjøretøy hvis det lagrede er slettet
    // (f.eks. fjernet fra en annen enhet og synket hit)
    val effective = available.firstOrNull { it.id == stored }?.id
        ?: available.firstOrNull()?.id

    return AmbulanceSelection(selectedId = effective) { id ->
        prefs.edit().putString(KEY_SELECTED, id).apply()
        stored = id
    }
}
