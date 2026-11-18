package app.danebrain.core

/**
 * A small Markdown reader for showing vault notes: enough of the syntax Obsidian notes use to be
 * read comfortably, not a full implementation. Parsing only; drawing is the UI's job.
 */
object Markdown {

    sealed interface Block
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    /** [marker] is the number text for ordered items ("1."), null for bullets. [checked] is null unless it is a task. */
    data class ListItem(val indent: Int, val marker: String?, val checked: Boolean?, val text: String) : Block
    data class Quote(val text: String) : Block
    data class Code(val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block
    /** A `![[file]]` on a line of its own. */
    data class Embed(val target: String) : Block
    data object Rule : Block

    data class Doc(val properties: List<Pair<String, String>>, val blocks: List<Block>)

    /** One run of inline text with its styling. [link] is a note name or a URL, told apart by [linkIsNote]. */
    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strike: Boolean = false,
        val highlight: Boolean = false,
        val link: String? = null,
        val linkIsNote: Boolean = false,
    )

    private val HEADING = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val LIST_ITEM = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(?:\\[([ xX])]\\s+)?(.*)$")
    private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val EMBED_LINE = Regex("^\\s*!\\[\\[([^\\]]+)]]\\s*$")
    private val TABLE_DIVIDER = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")
    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

    fun parse(source: String): Doc {
        var lines = source.replace("\r\n", "\n").split('\n')
        val properties = ArrayList<Pair<String, String>>()
        if (lines.firstOrNull()?.trim() == "---") {
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end >= 0) {
                properties.addAll(frontmatter(lines.subList(1, end + 1)))
                lines = lines.drop(end + 2)
            }
        }
        lines = COMMENT.replace(lines.joinToString("\n"), "").split('\n')

        val blocks = ArrayList<Block>()
        val paragraph = ArrayList<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks.add(Paragraph(paragraph.joinToString("\n")))
                paragraph.clear()
            }
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    flush()
                    val fence = trimmed.take(3)
                    val code = ArrayList<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) code.add(lines[i++])
                    blocks.add(Code(code.joinToString("\n")))
                }
                trimmed.isEmpty() -> flush()
                HEADING.matches(line) -> {
                    flush()
                    val m = HEADING.find(line)!!
                    blocks.add(Heading(m.groupValues[1].length, m.groupValues[2]))
                }
                RULE.matches(line) -> {
                    flush()
                    blocks.add(Rule)
                }
                EMBED_LINE.matches(line) -> {
                    flush()
                    blocks.add(Embed(EMBED_LINE.find(line)!!.groupValues[1].substringBefore('|').trim()))
                }
                trimmed.startsWith(">") -> {
                    flush()
                    val quote = ArrayList<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quote.add(lines[i].trim().removePrefix(">").removePrefix(" "))
                        i++
                    }
                    i--
                    blocks.add(Quote(quote.joinToString("\n")))
                }
                trimmed.startsWith("|") && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1]) -> {
                    flush()
                    val rows = ArrayList<List<String>>()
                    rows.add(cells(line))
                    i += 2
                    while (i < lines.size && lines[i].trim().startsWith("|")) rows.add(cells(lines[i++]))
                    i--
                    blocks.add(Table(rows))
                }
                LIST_ITEM.matches(line) -> {
                    flush()
                    val m = LIST_ITEM.find(line)!!
                    val bullet = m.groupValues[2]
                    val check = m.groupValues[3]
                    blocks.add(
                        ListItem(
                            indent = m.groupValues[1].replace("\t", "    ").length / 2,
                            marker = if (bullet.first().isDigit()) bullet.dropLast(1) + "." else null,
                            checked = if (check.isEmpty()) null else check != " ",
                            text = m.groupValues[4],
                        )
                    )
                }
                else -> paragraph.add(trimmed)
            }
            i++
        }
        flush()
        return Doc(properties, blocks)
    }

    private fun cells(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    /** Top-level `key: value` pairs. Indented and list lines are folded into the key above them. */
    private fun frontmatter(lines: List<String>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (line in lines) {
            if (line.isBlank()) continue
            val continuation = line.startsWith(" ") || line.startsWith("\t") || line.trimStart().startsWith("- ")
            if (continuation && out.isNotEmpty()) {
                val (key, value) = out.removeAt(out.size - 1)
                val piece = line.trim().removePrefix("- ").trim()
                out.add(key to if (value.isEmpty()) piece else "$value, $piece")
            } else {
                val colon = line.indexOf(':')
                if (colon > 0) out.add(line.substring(0, colon).trim() to line.substring(colon + 1).trim())
            }
        }
        return out
    }

    private val INLINE = Regex(
        "`([^`]+)`" +                                        // 1 code
            "|!?\\[\\[([^\\]]+)]]" +                         // 2 wikilink or inline embed
            "|\\[([^\\]]+)]\\(([^)\\s]+)\\)" +               // 3 text, 4 url
            "|\\*\\*(.+?)\\*\\*" +                           // 5 bold
            "|==(.+?)==" +                                   // 6 highlight
            "|~~(.+?)~~" +                                   // 7 strike
            "|(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])" + // 8 italic with *
            "|(?<!\\w)_(?!\\s)(.+?)(?<!\\s)_(?!\\w)"         // 9 italic with _
    )

    private val ESCAPE = Regex("""\\([!-/:-@\[-`{-~])""")
    private const val HIDDEN = 0xE000

    /**
     * Splits a line of text into styled runs. A backslash before punctuation (`\$`, `\*`) means
     * "this character is literal": it is hidden from the styling pass, then shown without the backslash.
     */
    fun inline(text: String): List<Span> {
        val shielded = ESCAPE.replace(text) { (HIDDEN + it.groupValues[1][0].code).toChar().toString() }
        return spans(shielded, Span("")).map { span ->
            span.copy(text = span.text.map { c -> if (c.code in HIDDEN..HIDDEN + 0x7F) (c.code - HIDDEN).toChar() else c }.joinToString(""))
        }
    }

    private fun spans(text: String, base: Span): List<Span> {
        val out = ArrayList<Span>()
        var pos = 0
        for (m in INLINE.findAll(text)) {
            if (m.range.first > pos) out.add(base.copy(text = text.substring(pos, m.range.first)))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> out.add(base.copy(text = g[1], code = true))
                g[2].isNotEmpty() -> {
                    val target = g[2].substringBefore('|').substringBefore('#').trim()
                    val label = g[2].substringAfter('|', "").trim().ifEmpty { target.substringAfterLast('/') }
                    out.add(base.copy(text = label.ifEmpty { g[2] }, link = target.ifEmpty { null }, linkIsNote = true))
                }
                g[3].isNotEmpty() -> out.add(base.copy(text = g[3], link = g[4], linkIsNote = false))
                g[5].isNotEmpty() -> out.addAll(spans(g[5], base.copy(bold = true)))
                g[6].isNotEmpty() -> out.addAll(spans(g[6], base.copy(highlight = true)))
                g[7].isNotEmpty() -> out.addAll(spans(g[7], base.copy(strike = true)))
                g[8].isNotEmpty() -> out.addAll(spans(g[8], base.copy(italic = true)))
                g[9].isNotEmpty() -> out.addAll(spans(g[9], base.copy(italic = true)))
            }
            pos = m.range.last + 1
        }
        if (pos < text.length) out.add(base.copy(text = text.substring(pos)))
        return out
    }
}
