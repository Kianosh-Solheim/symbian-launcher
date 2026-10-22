package com.example

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Save all launcher settings to a file, or restore them from one. */
@Composable
fun SettingsBackupScreen(haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(SettingsBackup.export(context).toString(2).toByteArray()) }; true
        } catch (_: Exception) { false }
        Toast.makeText(context, if (ok) "Backup saved" else "Could not save backup", Toast.LENGTH_SHORT).show()
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val err = try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            SettingsBackup.import(context, JSONObject(text))
        } catch (_: Exception) { "Not a valid backup file" }
        if (err == null) {
            Toast.makeText(context, "Settings restored — restarting", Toast.LENGTH_SHORT).show()
            SettingsBackup.restart(context)
        } else Toast.makeText(context, err, Toast.LENGTH_LONG).show()
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsMenuItem("Save backup…", null, haptic, isFirst = true) { save.launch(SettingsBackup.fileName()) }
            SettingsMenuItem("Restore from backup…", null, haptic) { restore.launch(arrayOf("application/json", "text/plain", "*/*")) }
            Text(
                "Saves the home screen, theme, pinned apps, weather, alarms, world clocks, wallet cards and Desktop Mode options. " +
                    "You can also download and restore backups from Desktop Mode → Tools.",
                color = Color(0xFFCCCCCC), fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
