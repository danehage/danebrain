package app.danebrain.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.danebrain.App
import app.danebrain.ChatMsg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(app: App) {
    val ui = app.ui
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(ui.chat.size, ui.chatBusy) {
        val last = ui.chat.size - 1 + if (ui.chatBusy) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || ui.chatBusy) return
        input = ""
        ui.chat.add(ChatMsg(fromUser = true, text = text))
        ui.chatBusy = true
        ui.chatStatus = "Thinking"
        app.scope.launch {
            try {
                val answer = withContext(Dispatchers.IO) {
                    app.chatEngine.send(text) { status -> app.scope.launch { ui.chatStatus = status } }
                }
                ui.chat.add(ChatMsg(fromUser = false, text = answer.ifBlank { "(empty reply)" }))
            } catch (e: Exception) {
                ui.chat.add(ChatMsg(fromUser = false, text = e.message ?: "Request failed", isError = true))
            } finally {
                ui.chatBusy = false
            }
        }
    }

    fun openNote(name: String) {
        val path = app.index.pathForStem(name.substringAfterLast('/').removeSuffix(".md"))
        if (path != null) ui.viewing.add(path)
        else Toast.makeText(context, "No note named $name", Toast.LENGTH_SHORT).show()
    }

    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Ask the vault", Modifier.weight(1f))
            TextButton(
                onClick = {
                    ui.chat.clear()
                    app.chatEngine.reset()
                },
                enabled = !ui.chatBusy && ui.chat.isNotEmpty(),
            ) { Text("New chat") }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (ui.chat.isEmpty()) {
                item {
                    Text("Ask your notes anything.", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Answers cite the notes they came from. Tap a citation to read the note.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        STARTERS.forEach { starter ->
                            Text(
                                starter,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(50))
                                    .clickable { input = starter }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
            itemsIndexed(ui.chat) { _, msg -> Message(msg, ::openNote) }
            if (ui.chatBusy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(ui.chatStatus, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val inputStyle = MaterialTheme.typography.bodyLarge
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surfaceContainerHigh, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                textStyle = inputStyle.copy(color = colors.onBackground),
                maxLines = 5,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { field ->
                    Box {
                        if (input.isEmpty()) {
                            Text("Ask a question", style = inputStyle, color = colors.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        field()
                    }
                },
            )
            Spacer(Modifier.width(10.dp))
            val canSend = input.isNotBlank() && !ui.chatBusy
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Palette.Teal else colors.surfaceContainerHigh)
                    .clickable(enabled = canSend, role = Role.Button, onClickLabel = "Send") { send() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) Color.White else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private val STARTERS = listOf(
    "What did I capture this week?",
    "Which bills are due soon?",
    "Which wines did I like most?",
)

/** Questions sit in a bubble on the right; answers run full width like a page, with tappable citations. */
@Composable
private fun Message(msg: ChatMsg, onLink: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    when {
        msg.isError -> SelectionContainer {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onErrorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.errorContainer, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            )
        }
        msg.fromUser -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            SelectionContainer {
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif),
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .background(colors.surfaceContainerHigh, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        else -> SelectionContainer {
            Text(
                formatAnswer(msg.text, colors.primary, onLink),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                color = colors.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val INLINE = Regex("\\[\\[([^\\]|#]+)(?:[|#][^\\]]*)?\\]\\]|\\*\\*(.+?)\\*\\*")

/** Renders [[wikilinks]] as tappable links and **bold** as bold; everything else is plain text. */
private fun formatAnswer(text: String, linkColor: Color, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var pos = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(pos, m.range.first))
        val link = m.groups[1]?.value
        if (link != null) {
            val name = link.trim()
            val annotation = LinkAnnotation.Clickable(
                tag = name,
                styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                linkInteractionListener = { onLink(name) },
            )
            withLink(annotation) { append(name) }
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groups[2]?.value.orEmpty()) }
        }
        pos = m.range.last + 1
    }
    append(text.substring(pos))
}
