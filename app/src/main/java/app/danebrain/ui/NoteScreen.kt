package app.danebrain.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danebrain.App
import app.danebrain.core.Markdown
import app.danebrain.data.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_NOTE_BYTES = 600_000L

private sealed interface Loaded {
    data object Reading : Loaded
    data class Ready(val doc: Markdown.Doc) : Loaded
    data class Failed(val reason: String) : Loaded
}

/** Colors and callbacks the inline renderer needs, gathered once per screen. */
private class InlineStyle(
    val link: SpanStyle,
    val code: SpanStyle,
    val highlight: SpanStyle,
    val onNote: (String) -> Unit,
    val onUrl: (String) -> Unit,
)

/**
 * Reads one vault note in place. Links to other notes open in the same viewer, building a back
 * stack in [app.danebrain.UiState.viewing]. Read-only: nothing here writes to the vault.
 */
@Composable
fun NoteScreen(app: App, path: String) {
    val ui = app.ui
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val loaded by produceState<Loaded>(initialValue = Loaded.Reading, path) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = app.vault.resolve(path)
                when {
                    !file.isFile -> Loaded.Failed("This note is no longer in the vault.")
                    file.length() > MAX_NOTE_BYTES -> Loaded.Failed("This note is too large to show here.")
                    else -> Loaded.Ready(Markdown.parse(file.readText()))
                }
            } catch (e: Exception) {
                Loaded.Failed(e.message ?: e.toString())
            }
        }
    }

    val style = InlineStyle(
        link = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
        code = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerHigh, fontSize = 14.sp),
        highlight = SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer),
        onNote = { name ->
            val target = app.index.pathForStem(name.substringAfterLast('/').removeSuffix(".md"))
            if (target != null) ui.viewing.add(target)
            else Toast.makeText(context, "No note named $name yet", Toast.LENGTH_SHORT).show()
        },
        onUrl = { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { Toast.makeText(context, "Could not open that link", Toast.LENGTH_SHORT).show() }
        },
    )

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ui.viewing.removeLastOrNull() }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (remember(path) { app.vault.isObsidianVault }) {
                TextButton(onClick = { app.vault.openInObsidian(context, path) }) { Text("Open in Obsidian") }
            }
        }
        when (val state = loaded) {
            Loaded.Reading -> {}
            is Loaded.Failed -> Text(
                state.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            is Loaded.Ready -> {
                val doc = state.doc
                val hasTitle = doc.blocks.firstOrNull().let { it is Markdown.Heading && it.level == 1 }
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                    ) {
                        item {
                            Text(
                                path.substringBeforeLast('/', ""),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = colors.onSurfaceVariant,
                            )
                            if (!hasTitle) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    path.substringAfterLast('/').removeSuffix(".md"),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                            if (doc.properties.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Properties(doc.properties)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        itemsIndexed(doc.blocks) { _, block -> BlockView(app, block, style) }
                    }
                }
            }
        }
    }
}

/** The note's frontmatter, as a quiet two-column card. */
@Composable
private fun Properties(properties: List<Pair<String, String>>) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(colors.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        properties.forEach { (key, value) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(
                    key,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.width(112.dp),
                )
                Text(value.ifEmpty { "–" }, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun BlockView(app: App, block: Markdown.Block, style: InlineStyle) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val body = type.bodyLarge.copy(lineHeight = 26.sp)
    when (block) {
        is Markdown.Heading -> {
            val heading: TextStyle = when (block.level) {
                1 -> type.headlineMedium
                2 -> type.titleLarge
                3 -> type.titleMedium
                else -> type.titleMedium.copy(fontSize = 16.sp, color = colors.onSurfaceVariant)
            }
            Text(
                rich(block.text, style),
                style = heading,
                modifier = Modifier.padding(top = if (block.level == 1) 10.dp else 22.dp, bottom = 6.dp),
            )
        }
        is Markdown.Paragraph -> Text(rich(block.text, style), style = body, modifier = Modifier.padding(vertical = 6.dp))
        is Markdown.ListItem -> Row(Modifier.padding(start = (block.indent * 18).dp, top = 3.dp, bottom = 3.dp)) {
            val bullet = when {
                block.checked == true -> "☑"
                block.checked == false -> "☐"
                block.marker != null -> block.marker
                else -> "•"
            }
            Text(bullet, style = body, color = colors.onSurfaceVariant, modifier = Modifier.width(26.dp))
            Text(
                rich(block.text, style),
                style = if (block.checked == true) body.copy(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough) else body,
            )
        }
        is Markdown.Quote -> {
            val bar = colors.primary
            Text(
                rich(block.text, style),
                style = body.copy(fontFamily = Serif, fontSize = 18.sp),
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .drawBehind { drawLine(bar, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 3.dp.toPx()) }
                    .padding(start = 16.dp),
            )
        }
        is Markdown.Code -> Box(
            Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .background(colors.surfaceContainerHigh, RoundedCornerShape(10.dp))
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(block.text, style = type.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 20.sp))
        }
        is Markdown.Table -> Column(Modifier.padding(vertical = 8.dp)) {
            block.rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    row.forEach { cell ->
                        Text(
                            rich(cell, style),
                            style = type.bodyMedium,
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                    }
                }
                HorizontalDivider(color = if (index == 0) colors.outline else colors.outlineVariant)
            }
        }
        is Markdown.Embed -> EmbedView(app, block.target, style)
        Markdown.Rule -> HorizontalDivider(Modifier.padding(vertical = 14.dp), color = colors.outlineVariant)
    }
}

/** An embedded image is shown; any other embedded file or note becomes a link to it. */
@Composable
private fun EmbedView(app: App, target: String, style: InlineStyle) {
    val isImage = remember(target) { target.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif") }
    if (!isImage) {
        Text(
            rich("[[$target]]", style),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        return
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, target) {
        value = withContext(Dispatchers.IO) {
            app.vault.findAttachment(target)?.let { ImageUtil.preview(it.path, 1400)?.asImageBitmap() }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            image,
            contentDescription = "Embedded image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Text(
            target,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

/** Turns a line of Markdown into styled text with tappable links. */
private fun rich(text: String, style: InlineStyle): AnnotatedString = buildAnnotatedString {
    for (span in Markdown.inline(text)) {
        var look = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            textDecoration = if (span.strike) TextDecoration.LineThrough else null,
        )
        if (span.code) look = look.merge(style.code)
        if (span.highlight) look = look.merge(style.highlight)
        val target = span.link
        if (target != null) {
            val annotation = LinkAnnotation.Clickable(
                tag = target,
                styles = TextLinkStyles(look.merge(style.link)),
                linkInteractionListener = { if (span.linkIsNote) style.onNote(target) else style.onUrl(target) },
            )
            withLink(annotation) { append(span.text) }
        } else {
            withStyle(look) { append(span.text) }
        }
    }
}
