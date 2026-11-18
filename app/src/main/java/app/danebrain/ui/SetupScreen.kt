package app.danebrain.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.danebrain.App
import app.danebrain.R
import app.danebrain.data.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Opens the system page where all-files access is granted to this app. */
fun openAllFilesAccess(context: Context) {
    val intent = Intent(
        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

/** First-run setup: four short steps, each ticking itself off when done. */
@Composable
fun SetupScreen(app: App) {
    val ui = app.ui
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val appName = stringResource(R.string.app_name)

    val hasAccess = remember(ui.resumeTick) { app.vault.hasAccess() }
    // Bumped by the vault picker so the steps below it re-check the vault.
    var vaultTick by remember { mutableStateOf(0) }
    val vaultOk = remember(ui.resumeTick, vaultTick) { app.vault.problem() == null }
    var apiKey by remember { mutableStateOf(app.settings.apiKey) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Eyebrow("Welcome")
        Spacer(Modifier.height(10.dp))
        Text("Set up $appName", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "It files what you scan, say and type into your notes folder as Markdown files. It only ever adds new files.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        Step(1, "Let it reach your vault", done = hasAccess) {
            Text(
                "Your vault is a folder on this phone. Android needs your permission before another app can read and write it.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            if (!hasAccess) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = { openAllFilesAccess(context) }) { Text("Grant all-files access") }
            }
        }

        Step(2, "Choose your vault", done = vaultOk) {
            if (!hasAccess) {
                Text("Finish step 1 first.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            } else {
                VaultPicker(app, onChanged = { vaultTick++ })
                if (!vaultOk) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "A vault is any folder of Markdown notes. Use Browse to pick one, or copy the starter vault " +
                            "from this project's page into your Documents folder first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        Step(3, "Where should notes go?", done = vaultOk) {
            if (!vaultOk) {
                Text("Choose a vault first.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            } else {
                RoutesChooser(app, tick = vaultTick, onChanged = { vaultTick++ })
            }
        }

        Step(4, "Add a model key", done = apiKey.isNotBlank(), optional = true) {
            Text(
                "Reading scans, filing and the Ask tab use a model through OpenRouter. " +
                    "Saving notes as they are and searching work without a key, so this can wait.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            LineField(
                label = "OpenRouter API key",
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    app.settings.apiKey = it
                },
                password = true,
                placeholder = "sk-or-…",
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.height(8.dp))
            KeyTester(app, enabled = apiKey.isNotBlank())
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                app.settings.setupDone = true
                app.loadRoutes()
                app.refreshIndex()
                ui.setup = false
            },
            enabled = hasAccess && vaultOk,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Teal, contentColor = Color.White),
        ) { Text("Start capturing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(24.dp))
    }
}

/** One numbered step: a badge that becomes a tick, a title, and the step's own content. */
@Composable
private fun Step(number: Int, title: String, done: Boolean, optional: Boolean = false, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(if (done) Palette.Teal else colors.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
            } else {
                Text("$number", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (optional) {
                    Spacer(Modifier.width(8.dp))
                    Eyebrow("Optional")
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/**
 * The vault path with three ways to set it: find it automatically, browse for the folder, or type
 * it. Saves the choice and reloads the routes and index. [onChanged] fires after each change.
 */
@Composable
fun VaultPicker(app: App, onChanged: () -> Unit = {}) {
    val ui = app.ui
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var vaultPath by remember { mutableStateOf(app.settings.vaultPath) }
    var candidates by remember { mutableStateOf(emptyList<File>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val hasAccess = remember(ui.resumeTick) { app.vault.hasAccess() }
    val problem = remember(ui.resumeTick, vaultPath) { app.vault.problem() }

    fun choose(path: String) {
        vaultPath = path
        app.settings.vaultPath = path
        candidates = emptyList()
        app.loadRoutes()
        app.refreshIndex()
        onChanged()
    }

    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = app.vault.pathFromTreeUri(uri)
            if (path == null) message = "That folder is not on this phone's storage."
            else {
                choose(path)
                message = null
            }
        }
    }

    LineField(
        label = "Vault folder",
        value = vaultPath,
        onValueChange = { choose(it) },
        placeholder = "/storage/emulated/0/Documents/MyVault",
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        problem ?: "Vault OK. ${if (ui.indexing) "Indexing" else ui.indexStatus}",
        color = if (problem != null && vaultPath.isNotBlank()) colors.error else colors.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            enabled = hasAccess,
            onClick = {
                message = "Looking"
                scope.launch {
                    val found = withContext(Dispatchers.IO) { app.vault.detect() }
                    when (found.size) {
                        0 -> message = "No Obsidian vault found in shared storage. Use Browse to pick any folder of notes. A vault kept in Obsidian's own app storage has to be moved to device storage first."
                        1 -> {
                            choose(found.first().path)
                            message = "Found ${found.first().name}"
                        }
                        else -> {
                            candidates = found
                            message = "Found ${found.size} vaults. Pick one."
                        }
                    }
                }
            },
        ) { Text("Find my vault") }
        OutlinedButton(enabled = hasAccess, onClick = { browse.launch(null) }) { Text("Browse") }
    }
    message?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    candidates.forEach { dir ->
        TextButton(onClick = { choose(dir.path) }) { Text(dir.path) }
    }
}

/**
 * Where notes are filed. With a routes file in the vault it just reports it. Without one it lets
 * the user pick the single inbox folder, and offers to write a starter routes file.
 */
@Composable
fun RoutesChooser(app: App, tick: Int = 0, onChanged: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    var message by remember { mutableStateOf<String?>(null) }
    val folders = remember(tick, app.routesSource) { app.vault.topFolders() }
    var inbox by remember(tick, app.routesSource) { mutableStateOf(app.settings.inboxFolder) }

    Text(app.routesSource, style = MaterialTheme.typography.bodyMedium)
    if (!app.hasRoutesFile) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick the folder for new notes. If it does not exist yet, it is created on the first save.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        val options = (listOf(inbox) + folders).filter { it.isNotBlank() }.distinct()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { folder ->
                val on = folder == inbox
                Text(
                    folder,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Color.White else colors.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Palette.Teal else Color.Transparent)
                        .border(1.dp, if (on) Palette.Teal else colors.outlineVariant, RoundedCornerShape(50))
                        .clickable(role = Role.RadioButton) {
                            inbox = folder
                            app.settings.inboxFolder = folder
                            app.loadRoutes()
                            onChanged()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "To file into several folders, add a routes file: a short list of folders, each with a sentence saying what belongs there.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            message = try {
                val count = app.vault.createStarterRoutes()
                app.loadRoutes()
                onChanged()
                "Wrote ${Vault.ROUTES_FILE} with $count routes. Edit its \"when\" sentences in any text editor."
            } catch (e: Exception) {
                "Not created: ${e.message}"
            }
        }) { Text("Create a routes file from my folders") }
    }
    message?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
}

/** A button that checks the key against the endpoint and shows the answer. */
@Composable
fun KeyTester(app: App, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            enabled = enabled && !busy,
            onClick = {
                busy = true
                result = null
                scope.launch {
                    try {
                        result = withContext(Dispatchers.IO) { app.api.test() }
                        failed = false
                    } catch (e: Exception) {
                        result = "Did not work: ${e.message ?: e.toString()}"
                        failed = true
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(if (busy) "Testing…" else "Test key") }
        Spacer(Modifier.width(12.dp))
        result?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) colors.error else colors.onSurfaceVariant)
        }
    }
}
