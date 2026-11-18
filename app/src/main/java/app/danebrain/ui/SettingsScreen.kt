package app.danebrain.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.danebrain.App
import app.danebrain.data.Settings

@Composable
fun SettingsScreen(app: App) {
    val ui = app.ui
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    var apiKey by remember { mutableStateOf(app.settings.apiKey) }
    var baseUrl by remember { mutableStateOf(app.settings.apiBaseUrl) }
    var visionModel by remember { mutableStateOf(app.settings.visionModel) }
    var chatModel by remember { mutableStateOf(app.settings.chatModel) }
    var vaultTick by remember { mutableStateOf(0) }

    val hasAccess = remember(ui.resumeTick) { app.vault.hasAccess() }
    val problem = remember(ui.resumeTick, vaultTick) { app.vault.problem() }
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

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
            Button(onClick = { openAllFilesAccess(context) }) { Text("Grant all-files access") }
            Spacer(Modifier.height(16.dp))
        }
        VaultPicker(app, onChanged = { vaultTick++ })
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { app.refreshIndex() }, enabled = problem == null && !ui.indexing) { Text("Reindex the vault") }

        if (problem == null) {
            Spacer(Modifier.height(14.dp))
            Eyebrow("Filing routes")
            Spacer(Modifier.height(6.dp))
            RoutesChooser(app, tick = vaultTick + ui.resumeTick, onChanged = { vaultTick++ })
            Spacer(Modifier.height(14.dp))
            Eyebrow("Scans are kept in")
            Spacer(Modifier.height(6.dp))
            Text(
                remember(vaultTick, ui.resumeTick) { "${app.vault.attachmentRoot()}/scans/" },
                style = mono,
            )
        }

        Spacer(Modifier.height(32.dp))
        Text("Model", style = MaterialTheme.typography.titleLarge)
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
        Spacer(Modifier.height(8.dp))
        KeyTester(app)
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
        Spacer(Modifier.height(20.dp))
        LineField(
            label = "API address",
            value = baseUrl,
            onValueChange = {
                baseUrl = it
                app.settings.apiBaseUrl = it
            },
            placeholder = Settings.DEFAULT_BASE_URL,
            textStyle = mono,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (app.settings.usesOpenRouter) {
                "Requests are limited to providers that do not retain or train on prompts. " +
                    "Scans and typed text are sent for filing; chat sends the notes it reads."
            } else {
                "Using a custom OpenAI-compatible address. Scans and typed text are sent there for filing; " +
                    "chat sends the notes it reads. What it keeps is up to that service."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { ui.setup = true }) { Text("Run setup again") }
    }
}
