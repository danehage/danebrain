package app.danebrain.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.danebrain.App
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.danebrain.data.DeviceCalendar
import app.danebrain.data.DeviceCalendars
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
            if (remember(vaultTick, ui.resumeTick) { app.vault.isObsidianVault }) {
                Spacer(Modifier.height(18.dp))
                Eyebrow("Obsidian Sync")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Obsidian only syncs while it is open. This adds a home-screen button that opens it on this vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    if (!app.vault.pinSyncShortcut(context)) {
                        Toast.makeText(context, "This home screen cannot add shortcuts", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Add a Sync Obsidian button") }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("Calendar", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        CalendarChooser(app)

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

/**
 * The calendar feature, off unless the user turns it on. Turning it on lists the calendars already
 * on the phone to pick from. Even then nothing is added automatically: each dated item has its own
 * switch on the review screen, off by default.
 */
@Composable
private fun CalendarChooser(app: App) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var chosenName by remember { mutableStateOf(app.settings.calendarName.takeIf { app.settings.calendarId >= 0 }.orEmpty()) }
    var label by remember { mutableStateOf(app.settings.calendarLabel.takeIf { it != Settings.DEFAULT_CALENDAR_LABEL }.orEmpty()) }
    var options by remember { mutableStateOf(emptyList<DeviceCalendar>()) }
    var choosing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun list() {
        options = DeviceCalendars.writable(context)
        choosing = options.isNotEmpty()
        message = if (options.isEmpty()) "No calendar on this phone can be added to." else null
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) list() else message = "Calendar permission is needed to add events."
    }
    fun pick() {
        if (DeviceCalendars.hasPermission(context)) list() else permission.launch(DeviceCalendars.PERMISSIONS)
    }
    fun turnOff() {
        app.settings.calendarId = -1
        app.settings.calendarName = ""
        chosenName = ""
        options = emptyList()
        choosing = false
        message = null
    }

    val on = chosenName.isNotEmpty() || choosing
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Offer dates for my calendar", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                "When a capture mentions a date, such as an appointment or a bill's due date, the review screen " +
                    "lists it with a switch. Only the ones you switch on are added. Leave this off if you do not want it.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = on,
            onCheckedChange = { if (it) pick() else turnOff() },
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Teal, checkedThumbColor = Color.White),
        )
    }
    if (choosing) {
        Spacer(Modifier.height(10.dp))
        Eyebrow("Which calendar?")
        options.forEach { calendar ->
            val line = if (calendar.account.isEmpty() || calendar.account == calendar.name) calendar.name else "${calendar.name} · ${calendar.account}"
            TextButton(onClick = {
                app.settings.calendarId = calendar.id
                app.settings.calendarName = line
                chosenName = line
                options = emptyList()
                choosing = false
            }) { Text(line) }
        }
    }
    if (chosenName.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Eyebrow("Adds to")
        Spacer(Modifier.height(6.dp))
        Text(chosenName, style = MaterialTheme.typography.bodyMedium)
        if (!choosing) TextButton(onClick = { pick() }) { Text("Change calendar") }
        Spacer(Modifier.height(8.dp))
        LineField(
            label = "What to call it",
            value = label,
            onValueChange = {
                label = it
                app.settings.calendarLabel = it
            },
            placeholder = "calendar",
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "The switch on the review screen will read \"Add to ${label.trim().ifEmpty { Settings.DEFAULT_CALENDAR_LABEL }}\". " +
                "Name it after wherever the calendar shows up, such as a wall display.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
    message?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
}
