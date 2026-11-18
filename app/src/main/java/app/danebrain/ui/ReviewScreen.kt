package app.danebrain.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.danebrain.App
import app.danebrain.core.Analysis
import app.danebrain.core.NoteBuilder
import app.danebrain.core.NoteDraft
import app.danebrain.core.Routes
import app.danebrain.core.Text as TextUtil
import app.danebrain.data.CaptureRepo
import app.danebrain.data.ImageUtil
import app.danebrain.work.AnalyzeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

private val FIELD_LABELS = mapOf(
    "producer" to "Producer",
    "wine" to "Wine",
    "vintage" to "Vintage",
    "grapes" to "Grapes (comma-separated)",
    "place" to "Place",
    "country" to "Country",
    "color" to "Color",
    "price" to "Price",
    "where" to "Where",
    "served" to "Served",
    "again" to "Buy again (yes / no / maybe)",
    "vendor" to "Vendor",
    "doc_kind" to "Kind (invoice, bill, receipt...)",
    "amount" to "Amount",
    "currency" to "Currency",
    "doc_date" to "Document date (YYYY-MM-DD)",
    "due_date" to "Due date (YYYY-MM-DD)",
    "account_last4" to "Account, last 4",
)

@Composable
fun ReviewScreen(app: App, id: Long) {
    val ui = app.ui
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by app.captures.changes.collectAsState()
    val capture = remember(id, tick) { app.captures.get(id) }
    if (capture == null) {
        LaunchedEffect(id) { ui.reviewId = null }
        return
    }

    // Editable state is rebuilt when the analysis arrives or changes, not on every recomposition.
    val analysisKey = capture.analysis
    val analysis = remember(analysisKey) { analysisKey?.let { runCatching { Analysis.parse(it) }.getOrNull() } }
    var routeKey by remember(analysisKey) {
        mutableStateOf(Routes.find(app.routes, analysis?.route).key)
    }
    var title by remember(analysisKey) {
        mutableStateOf(analysis?.title ?: capture.userText.takeIf { it.isNotBlank() }?.let { TextUtil.titleFromText(it) } ?: "")
    }
    val fields = remember(analysisKey) {
        mutableStateMapOf<String, String>().apply { analysis?.fields?.let { putAll(it) } }
    }
    var tagsText by remember(analysisKey) { mutableStateOf(analysis?.tags.orEmpty().joinToString(", ")) }
    var summary by remember(analysisKey) { mutableStateOf(analysis?.summary.orEmpty()) }
    var transcription by remember(analysisKey) { mutableStateOf(analysis?.transcription.orEmpty()) }
    var userText by remember(id) { mutableStateOf(capture.userText) }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var routeMenu by remember { mutableStateOf(false) }

    val route = Routes.find(app.routes, routeKey)
    val date = remember(capture.createdAt) {
        Instant.ofEpochMilli(capture.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
    val draft = NoteDraft(
        route = route,
        title = title.trim(),
        summary = summary,
        transcription = transcription,
        tags = tagsText.split(',').map { TextUtil.slugify(it, 30) }.filter { it.isNotEmpty() }.distinct(),
        fields = fields.filterValues { it.isNotBlank() },
        userText = userText,
        pageCount = capture.pages.size,
        date = date,
        unprocessed = analysis == null,
    )

    fun save() {
        saving = true
        error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { app.writeToVault(draft, capture.pages.map { File(it) }) }
                app.captures.delete(id)
                Toast.makeText(context, "Saved to ${result.notePath}", Toast.LENGTH_LONG).show()
                app.refreshIndex()
                ui.reviewId = null
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                saving = false
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val titleStyle = MaterialTheme.typography.headlineMedium
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ui.reviewId = null }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { confirmDiscard = true }, enabled = !saving) {
                Text("Discard", color = colors.error)
            }
        }

        if (capture.pages.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                capture.pages.forEach { path -> PagePreview(path) }
            }
            Spacer(Modifier.height(20.dp))
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            when {
                capture.status == CaptureRepo.STATUS_FAILED -> {
                    Column(
                        Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(14.dp)).padding(14.dp)
                    ) {
                        Text(
                            "Analysis failed: ${capture.error.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onErrorContainer,
                        )
                        Text(
                            "Try again, or fill in a title and save it by hand. It will be tagged unprocessed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onErrorContainer,
                        )
                        TextButton(onClick = {
                            app.captures.retry(id)
                            AnalyzeWorker.enqueue(context)
                        }) { Text("Try again", color = colors.onErrorContainer, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(18.dp))
                }
                analysis == null -> {
                    Text(
                        "Still reading this one. Wait, or fill in a title and save it by hand now; it will be tagged unprocessed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }

            Eyebrow(if (analysis != null) "${route.key} · suggested" else route.key)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = titleStyle.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { field ->
                    Box {
                        if (title.isEmpty()) Text("Title", style = titleStyle, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                        field()
                    }
                },
            )
            Spacer(Modifier.height(18.dp))

            Box {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceContainerHigh)
                        .clickable { routeMenu = true }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("File as", Modifier.weight(1f))
                        Text("Change", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(route.key, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${route.folder}/${NoteBuilder.stem(draft)}.md",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = routeMenu, onDismissRequest = { routeMenu = false }) {
                    app.routes.forEach { r ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(r.key)
                                    Text(r.folder, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                routeKey = r.key
                                routeMenu = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Analysis.fieldsFor(route.format).forEach { key ->
                    LineField(
                        label = FIELD_LABELS[key] ?: key,
                        value = fields[key].orEmpty(),
                        onValueChange = { fields[key] = it },
                    )
                }
                LineField(
                    label = "Your words (saved exactly as written)",
                    value = userText,
                    onValueChange = { userText = it },
                    singleLine = false,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif),
                    placeholder = "Nothing yet",
                )
                if (route.format != Routes.FORMAT_WINE) {
                    LineField(
                        label = "Tags (comma-separated)",
                        value = tagsText,
                        onValueChange = { tagsText = it },
                    )
                }
                if (route.format == Routes.FORMAT_DOCUMENT) {
                    LineField(
                        label = "Summary",
                        value = summary,
                        onValueChange = { summary = it },
                        singleLine = false,
                    )
                }
                if (capture.pages.isNotEmpty()) {
                    LineField(
                        label = "Transcription (check numbers against the scan)",
                        value = transcription,
                        onValueChange = { transcription = it },
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxHeight = 360.dp,
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(14.dp))
                Text("Not saved: $it", style = MaterialTheme.typography.bodyMedium, color = colors.error)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { save() },
                enabled = !saving && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Teal, contentColor = Color.White),
            ) {
                Text(if (saving) "Filing…" else "File it", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this capture?") },
            text = { Text("The scan and text are removed from the queue. Nothing in the vault changes.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    app.captures.delete(id)
                    ui.reviewId = null
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun PagePreview(path: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { ImageUtil.preview(path) }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Scanned page",
            modifier = Modifier.height(280.dp).clip(RoundedCornerShape(8.dp)),
        )
    }
}
