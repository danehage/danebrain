package app.danebrain.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danebrain.App
import app.danebrain.R
import app.danebrain.Tab
import app.danebrain.core.Analysis
import app.danebrain.core.Text as TextUtil
import app.danebrain.data.Capture
import app.danebrain.data.CaptureRepo
import app.danebrain.data.ImageUtil
import app.danebrain.work.AnalyzeWorker
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Home: three ways to capture, the draft in progress, and the queue of captures waiting for review. */
@Composable
fun CaptureScreen(app: App, onSpeak: () -> Unit) {
    val ui = app.ui
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val tick by app.captures.changes.collectAsState()
    val queue = remember(tick) { app.captures.list() }
    val problem = remember(tick, ui.resumeTick, ui.tab) { app.vault.problem() }
    val hasKey = remember(ui.resumeTick, ui.tab) { app.settings.canCallModel }
    val waiting = remember(ui.resumeTick, ui.syncTick) { app.vault.waitingForSync().size }

    /** Stages scanned pages outside the vault and queues them, with the draft text as the user's words. */
    fun queueScan(uris: List<Uri>) {
        val words = ui.draft.trim()
        app.scope.launch {
            val files = ArrayList<File>()
            try {
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        val dest = app.captures.newStagingFile()
                        ImageUtil.importJpeg(context, uri, dest)
                        files.add(dest)
                    }
                }
                app.captures.add(CaptureRepo.KIND_SCAN, words, files)
                ui.draft = ""
                app.settings.draft = ""
                AnalyzeWorker.enqueue(context)
            } catch (e: Exception) {
                files.forEach { it.delete() }
                Toast.makeText(context, "Scan was not saved: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages.orEmpty().map { it.imageUri }
            if (uris.isNotEmpty()) queueScan(uris)
        }
    }

    fun startScan() {
        val activity = context as? Activity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender -> scanner.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e -> Toast.makeText(context, "Scanner unavailable: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(ui.scanRequested) {
        if (ui.scanRequested) {
            ui.scanRequested = false
            startScan()
        }
    }

    val today = remember(ui.resumeTick) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · d MMM")).uppercase()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
    ) {
        item {
            Eyebrow(today)
            Spacer(Modifier.height(10.dp))
            Text(
                buildAnnotatedString {
                    append("What's on ")
                    withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append("your mind?") }
                },
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(18.dp))
        }
        if (problem != null || !hasKey) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.errorContainer)
                        .clickable { ui.tab = Tab.Settings }
                        .padding(14.dp)
                ) {
                    Text(
                        problem ?: "No API key yet. Notes can be saved raw; scans and filing wait in the queue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onErrorContainer,
                    )
                    Text(
                        "Open settings",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onErrorContainer,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        if (ui.draft.isNotBlank()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceContainerHigh)
                        .clickable { ui.writing = true }
                        .padding(14.dp)
                ) {
                    Eyebrow("Draft")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ui.draft.trim(),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap to finish it, or scan and it goes on that note as your words.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Scan paper", R.drawable.ic_scan, primary = true, Modifier.weight(1.25f)) { startScan() }
                ActionTile("Speak", R.drawable.ic_mic, primary = false, Modifier.weight(1f), onSpeak)
                ActionTile("Write", R.drawable.ic_pen, primary = false, Modifier.weight(1f)) { ui.writing = true }
            }
        }
        if (waiting > 0) {
            item {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.tertiaryContainer)
                        .clickable(role = Role.Button) { app.vault.openObsidian(context) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (waiting == 1) "1 note waiting to sync" else "$waiting notes waiting to sync",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onTertiaryContainer,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Saved since Obsidian was last open. It syncs to your other devices while Obsidian is open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onTertiaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Open Obsidian",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onTertiaryContainer,
                    )
                }
            }
        }
        if (queue.isNotEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Waiting for you · ${queue.size}", Modifier.weight(1f))
                    if (queue.any { it.status == CaptureRepo.STATUS_PENDING }) {
                        TextButton(onClick = { AnalyzeWorker.enqueue(context) }) { Text("Run now") }
                    } else {
                        Spacer(Modifier.height(48.dp))
                    }
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
            items(queue, key = { it.id }) { capture ->
                QueueRow(capture) { ui.reviewId = capture.id }
                HorizontalDivider(color = colors.outlineVariant)
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: Int, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = if (primary) Color.White else colors.onSurface
    Column(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) Palette.Teal else colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(26.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content)
    }
}

@Composable
private fun QueueRow(capture: Capture, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val analysis = remember(capture.analysis) {
        capture.analysis?.let { runCatching { Analysis.parse(it) }.getOrNull() }
    }
    val title = analysis?.title
        ?: capture.userText.takeIf { it.isNotBlank() }?.let { TextUtil.titleFromText(it) }
        ?: "Scan, ${capture.pages.size} page${if (capture.pages.size == 1) "" else "s"}"
    val time = remember(capture.createdAt) {
        Instant.ofEpochMilli(capture.createdAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM · HH:mm"))
    }
    val kind = if (capture.pages.isEmpty()) "Text" else "${capture.pages.size} page${if (capture.pages.size == 1) "" else "s"}"
    val detail = when {
        capture.status == CaptureRepo.STATUS_FAILED -> "Needs filing by hand: ${capture.error.orEmpty()}"
        capture.error != null -> "Waiting to retry: ${capture.error}"
        else -> "$kind · $time"
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(capture)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (capture.status == CaptureRepo.STATUS_FAILED) colors.error else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        when (capture.status) {
            CaptureRepo.STATUS_READY -> Chip("Review", colors.tertiaryContainer, colors.onTertiaryContainer)
            CaptureRepo.STATUS_FAILED -> Chip("Needs you", colors.errorContainer, colors.onErrorContainer)
            else -> Chip(if (capture.error != null) "Retrying" else "Reading…", colors.surfaceVariant, colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Chip(label: String, background: Color, content: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** The first scanned page as a small portrait thumbnail; a pen on a blank tile for text captures. */
@Composable
private fun Thumb(capture: Capture) {
    val path = capture.pages.firstOrNull()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = path?.let { withContext(Dispatchers.IO) { ImageUtil.preview(it, 200)?.asImageBitmap() } }
    }
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 60.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                painterResource(R.drawable.ic_pen),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
