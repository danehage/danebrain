package app.danebrain.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.danebrain.App
import app.danebrain.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(app: App) {
    val ui = app.ui
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var vaultPath by remember { mutableStateOf(app.settings.vaultPath) }
    var apiKey by remember { mutableStateOf(app.settings.apiKey) }
    var visionModel by remember { mutableStateOf(app.settings.visionModel) }
    var chatModel by remember { mutableStateOf(app.settings.chatModel) }
    var candidates by remember { mutableStateOf(emptyList<File>()) }
    var detectMessage by remember { mutableStateOf<String?>(null) }

    val hasAccess = remember(ui.resumeTick) { app.vault.hasAccess() }
    val problem = remember(ui.resumeTick, vaultPath) { app.vault.problem() }

    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

    fun chooseVault(path: String) {
        vaultPath = path
        app.settings.vaultPath = path
        candidates = emptyList()
        app.loadRoutes()
        app.refreshIndex()
    }

    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)

        Spacer(Modifier.height(24.dp))
        Eyebrow("Appearance")
        Spacer(Modifier.height(10.dp))
        Segmented(
            options = listOf(
                Settings.THEME_SYSTEM to "Follow phone",
                Settings.THEME_LIGHT to "Light",
                Settings.THEME_DARK to "Dark",
            ),
            selected = ui.themeMode,
            onSelect = {
                ui.themeMode = it
                app.settings.themeMode = it
            },
        )

        Spacer(Modifier.height(32.dp))
        Text("Vault", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))
        if (!hasAccess) {
            Text(
                "The app needs all-files access to read and write the vault folder.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
                runCatching { context.startActivity(intent) }.onFailure {
                    context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }) { Text("Grant all-files access") }
            Spacer(Modifier.height(16.dp))
        }
        LineField(
            label = "Vault folder",
            value = vaultPath,
            onValueChange = {
                vaultPath = it
                app.settings.vaultPath = it
            },
            placeholder = "/storage/emulated/0/Documents/DaneBrain",
            textStyle = mono,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            problem ?: "Vault OK. ${if (ui.indexing) "Indexing" else ui.indexStatus}",
            color = if (problem != null) colors.error else colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = hasAccess,
                onClick = {
                    detectMessage = "Looking"
                    scope.launch {
                        val found = withContext(Dispatchers.IO) { app.vault.detect() }
                        when (found.size) {
                            0 -> detectMessage = "No Obsidian vault found in shared storage. If the vault lives in Obsidian's app storage, move it to device storage first."
                            1 -> {
                                chooseVault(found.first().path)
                                detectMessage = "Found ${found.first().name}"
                            }
                            else -> {
                                candidates = found
                                detectMessage = "Found ${found.size} vaults. Pick one."
                            }
                        }
                    }
                },
            ) { Text("Find my vault") }
            OutlinedButton(onClick = { app.refreshIndex() }, enabled = problem == null && !ui.indexing) { Text("Reindex") }
        }
        Spacer(Modifier.height(18.dp))
        Eyebrow("Filing routes")
        Spacer(Modifier.height(6.dp))
        Text(app.routesSource, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        detectMessage?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        candidates.forEach { dir ->
            TextButton(onClick = { chooseVault(dir.path) }) { Text(dir.path) }
        }

        Spacer(Modifier.height(32.dp))
        Text("OpenRouter", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))
        LineField(
            label = "API key",
            value = apiKey,
            onValueChange = {
                apiKey = it
                app.settings.apiKey = it
            },
            password = true,
            placeholder = "sk-or-…",
            textStyle = mono,
        )
        Spacer(Modifier.height(20.dp))
        LineField(
            label = "Model for scans and filing",
            value = visionModel,
            onValueChange = {
                visionModel = it
                app.settings.visionModel = it
            },
            textStyle = mono,
        )
        Spacer(Modifier.height(20.dp))
        LineField(
            label = "Model for chat",
            value = chatModel,
            onValueChange = {
                chatModel = it
                app.settings.chatModel = it
            },
            textStyle = mono,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Requests are limited to providers that do not retain or train on prompts. " +
                "Scans and typed text are sent for filing; chat sends the notes it reads.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}
