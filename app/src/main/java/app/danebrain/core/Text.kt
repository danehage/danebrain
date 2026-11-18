package app.danebrain.core

import java.text.Normalizer

/** Filename and YAML helpers. Pure Kotlin so they run in JVM unit tests. */
object Text {

    /** Kebab-case slug: lowercase ASCII letters and digits joined by single hyphens. */
    fun slugify(input: String, max: Int = 60): String {
        val ascii = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("ß", "ss")
            .lowercase()
        val slug = ascii.replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (slug.length <= max) return slug
        val cut = slug.substring(0, max)
        val lastHyphen = cut.lastIndexOf('-')
        return (if (lastHyphen > max / 2) cut.substring(0, lastHyphen) else cut).trim('-')
    }

    /** A title for text saved without a model call: the first line, cut to a few words. */
    fun titleFromText(text: String, maxWords: Int = 8): String {
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return "Untitled note"
        val cleaned = firstLine.trimStart('#', '-', '*', '>', ' ')
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "Untitled note"
        val title = words.take(maxWords).joinToString(" ").trimEnd('.', ',', ';', ':')
        return title.replaceFirstChar { it.uppercase() }
    }

    private val yamlPlain = Regex("^[A-Za-z0-9][A-Za-z0-9 _./()+-]*$")
    private val yamlReserved = setOf("true", "false", "yes", "no", "null", "on", "off", "~")
    private val yamlNumber = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)$")
    private val yamlDate = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** A YAML scalar, double-quoted whenever leaving it bare could change its meaning. */
    fun yamlString(value: String): String {
        val v = value.replace("\r", "").replace("\n", " ").trim()
        if (v.isEmpty()) return ""
        val safe = yamlPlain.matches(v) &&
            v.lowercase() !in yamlReserved &&
            !yamlNumber.matches(v) &&
            !yamlDate.matches(v) &&
            !v.endsWith(" ")
        return if (safe) v else "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    /** Always double-quoted, for values that must stay strings (wikilinks, digit strings). */
    fun yamlQuoted(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun yamlList(values: List<String>, alwaysQuote: Boolean = false): String =
        values.joinToString(", ", "[", "]") { if (alwaysQuote) yamlQuoted(it) else yamlString(it) }

    /** A number for frontmatter, or null if the text is not a plain amount. */
    fun parseAmount(raw: String): String? {
        val cleaned = raw.replace(Regex("[^0-9.,-]"), "")
        if (cleaned.isEmpty()) return null
        // "1,234.56" -> "1234.56"; "184,32" -> "184.32"
        val normalized = when {
            cleaned.contains('.') && cleaned.contains(',') ->
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) cleaned.replace(".", "").replace(',', '.')
                else cleaned.replace(",", "")
            cleaned.contains(',') ->
                if (Regex(",\\d{2}$").containsMatchIn(cleaned)) cleaned.replace(',', '.') else cleaned.replace(",", "")
            else -> cleaned
        }
        return normalized.toBigDecimalOrNull()?.toPlainString()
    }

    fun isIsoDate(value: String): Boolean = yamlDate.matches(value.trim())

    /** Markdown quote block of the user's words, unchanged apart from the "> " prefix. */
    fun quoteBlock(text: String): String =
        text.trim().lines().joinToString("\n") { if (it.isBlank()) ">" else "> $it" }
}
