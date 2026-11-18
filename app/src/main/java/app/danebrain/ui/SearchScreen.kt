package app.danebrain.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.danebrain.App
import app.danebrain.data.SearchHit
import app.danebrain.data.SearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(app: App) {
    val ui = app.ui
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<SearchHit>()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Re-run when the query changes or an index refresh finishes.
    LaunchedEffect(query, ui.indexStatus) {
        delay(120)
        hits = withContext(Dispatchers.IO) { app.index.search(query, 60) }
    }

    val mark = SpanStyle(
        background = colors.tertiaryContainer,
        color = colors.onTertiaryContainer,
        fontWeight = FontWeight.SemiBold,
    )
    val queryStyle = MaterialTheme.typography.headlineSmall

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(if (ui.indexing) "Indexing…" else ui.indexStatus, Modifier.weight(1f))
            TextButton(onClick = { app.refreshIndex() }, enabled = !ui.indexing) { Text("Refresh") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focus).padding(vertical = 10.dp),
                textStyle = queryStyle.copy(color = colors.onBackground),
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Search the vault", style = queryStyle, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        field()
                    }
                },
            )
            if (query.isNotEmpty()) {
                TextButton(onClick = { query = "" }) { Text("Clear") }
            }
        }
        HorizontalDivider(thickness = 1.5.dp, color = colors.onBackground)
        if (query.isNotBlank() && hits.isEmpty()) {
            Text(
                "No notes match.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(hits, key = { it.path }) { hit ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { ui.viewing.add(hit.path) }
                        .padding(vertical = 14.dp)
                ) {
                    Text(hit.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        highlight(hit.snippet, mark),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        hit.path,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
        }
    }
}

/** Turns the index's match markers into highlighted text. */
private fun highlight(snippet: String, mark: SpanStyle): AnnotatedString = buildAnnotatedString {
    var rest = snippet
    while (true) {
        val start = rest.indexOf(SearchIndex.MARK_START)
        if (start < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, start))
        val end = rest.indexOf(SearchIndex.MARK_END, start)
        if (end < 0) {
            append(rest.substring(start + 1))
            break
        }
        withStyle(mark) { append(rest.substring(start + 1, end)) }
        rest = rest.substring(end + 1)
    }
}
