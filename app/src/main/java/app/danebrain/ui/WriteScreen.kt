package app.danebrain.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danebrain.App
import app.danebrain.R
import app.danebrain.core.NoteDraft
import app.danebrain.core.Routes
import app.danebrain.core.Text as TextUtil
import app.danebrain.data.CaptureRepo
import app.danebrain.work.AnalyzeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A full page for the draft note: type or dictate, then save it raw or let the model file it. */
@Composable
fun WriteScreen(app: App, onDictate: () -> Unit) {
    val ui = app.ui
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val problem = remember(ui.resumeTick) { app.vault.problem() }

    fun done() {
        ui.draft = ""
        app.settings.draft = ""
        ui.writing = false
    }

    /** Writes the text straight to the inbox with no model call, so it works offline. */
    fun saveRaw() {
        val text = ui.draft.trim()
        if (text.isEmpty()) return
        val draft = NoteDraft(
            route = Routes.default(app.routes),
            title = TextUtil.titleFromText(text),
            userText = text,
            date = LocalDate.now().toString(),
        )
        app.scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { app.writeToVault(draft, emptyList()) }
                done()
                Toast.makeText(context, "Saved to ${result.notePath}", Toast.LENGTH_SHORT).show()
                app.refreshIndex()
            } catch (e: Exception) {
                Toast.makeText(context, "Not saved: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Queues the text for the model to title and route; it is reviewed before anything is written. */
    fun saveWithModel() {
        val text = ui.draft.trim()
        if (text.isEmpty()) return
        app.captures.add(CaptureRepo.KIND_TEXT, text, emptyList())
        done()
        AnalyzeWorker.enqueue(context)
        Toast.makeText(context, "Queued for filing", Toast.LENGTH_SHORT).show()
    }

    // Open the keyboard only for a blank page; after dictation the words are there to read first.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (ui.draft.isBlank()) focus.requestFocus() }

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ui.writing = false }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDictate) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = "Dictate", tint = colors.primary)
            }
        }
        TextField(
            value = ui.draft,
            onValueChange = { ui.draft = it },
            modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(focus),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontSize = 19.sp, lineHeight = 29.sp),
            placeholder = {
                Text(
                    "What's on your mind?",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontSize = 19.sp),
                )
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        if (problem != null) {
            Text(
                problem,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { saveRaw() },
                enabled = ui.draft.isNotBlank() && problem == null,
                modifier = Modifier.weight(1f),
            ) { Text("Save raw") }
            Button(
                onClick = { saveWithModel() },
                enabled = ui.draft.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Save and file") }
        }
    }
}
