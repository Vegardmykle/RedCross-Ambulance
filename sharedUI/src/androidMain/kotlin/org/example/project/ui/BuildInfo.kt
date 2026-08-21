package org.example.project.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Hvilken app som faktisk kjører. Under testing er det lett å ta feil av
 * test- og produksjonsversjonen, og de snakker med hver sin database –
 * en forveksling kan bety at testdata havner i den ekte historikken.
 */
data class BuildInfo(
    val versionName: String,
    val versionCode: Long,
    val isDebug: Boolean,
    val packageName: String,
) {
    val environment: String get() = if (isDebug) "TEST" else "Produksjon"

    /** F.eks. «1.1 (2) · TEST» */
    val summary: String get() = "$versionName ($versionCode) · $environment"
}

@Composable
fun rememberBuildInfo(): BuildInfo {
    val context = LocalContext.current
    return remember(context) { context.readBuildInfo() }
}

private fun Context.readBuildInfo(): BuildInfo {
    val info = runCatching {
        packageManager.getPackageInfo(packageName, 0)
    }.getOrNull()

    @Suppress("DEPRECATION")
    val code = info?.let {
        if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode
        else it.versionCode.toLong()
    } ?: 0L

    return BuildInfo(
        versionName = info?.versionName ?: "?",
        versionCode = code,
        // Leses fra manifestet i stedet for BuildConfig, så modulen ikke
        // trenger å vite hvilken app den er bygget inn i
        isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        packageName = packageName,
    )
}
