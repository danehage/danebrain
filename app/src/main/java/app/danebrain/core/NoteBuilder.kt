package app.danebrain.core

/** Everything needed to write one note, after the user has reviewed it. */
data class NoteDraft(
    val route: Route,
    val title: String,
    val summary: String = "",
    val transcription: String = "",
    val tags: List<String> = emptyList(),
    val fields: Map<String, String> = emptyMap(),
    /** The user's own typed or dictated words. Written verbatim, never altered. */
    val userText: String = "",
    val pageCount: Int = 0,
    /** ISO date of capture. */
    val date: String,
    /** True when the model never saw this capture. */
    val unprocessed: Boolean = false,
)

object NoteBuilder {
    /** Default scans folder; the app passes the vault's own attachment folder instead. */
    const val IMAGE_ROOT = "90-Attachments/scans"

    /** Folder for scan images, relative to the vault root: one subfolder per year under [scansRoot]. */
    fun imageFolder(date: String, scansRoot: String = IMAGE_ROOT): String = "$scansRoot/${date.take(4)}"

    fun imageName(stem: String, page: Int): String = "$stem-p$page.jpg"

    /** Filename without extension, before any collision suffix. */
    fun stem(d: NoteDraft): String {
        val f = d.fields
        val titleSlug = Text.slugify(d.title).ifEmpty { "untitled" }
        return when (d.route.format) {
            Routes.FORMAT_WINE -> {
                val parts = listOf(f["vintage"], f["producer"], f["wine"]).filterNot { it.isNullOrBlank() }
                Text.slugify(parts.joinToString(" ")).ifEmpty { titleSlug }
            }
            Routes.FORMAT_DOCUMENT -> {
                val docDate = f["doc_date"]?.takeIf { Text.isIsoDate(it) } ?: d.date
                val vendor = Text.slugify(f["vendor"].orEmpty(), 30)
                val kind = Text.slugify(f["doc_kind"].orEmpty(), 20).ifEmpty { "document" }
                if (vendor.isEmpty()) "$docDate-$titleSlug" else "$docDate-$vendor-$kind"
            }
            else -> if (d.route.datePrefix) "${d.date}-$titleSlug" else titleSlug
        }
    }

    /**
     * The note's markdown. [stem] is the final filename (after collision handling), which the
     * image embeds are derived from. [noteExists] says whether a note with that filename stem is
     * already in the vault, so wine grapes and places only become wikilinks when the target exists.
     */
    fun render(d: NoteDraft, stem: String, noteExists: (String) -> Boolean = { false }): String =
        when (d.route.format) {
            Routes.FORMAT_WINE -> renderWine(d, stem, noteExists)
            Routes.FORMAT_DOCUMENT -> renderDocument(d, stem)
            else -> renderNote(d, stem)
        }

    private fun embeds(d: NoteDraft, stem: String): String =
        (1..d.pageCount).joinToString("\n") { "![[${imageName(stem, it)}]]" }

    private fun mergedTags(d: NoteDraft, extra: List<String> = emptyList()): List<String> {
        val all = LinkedHashSet<String>()
        all.addAll(d.route.tags)
        all.addAll(extra)
        all.addAll(d.tags)
        if (d.unprocessed) all.add("unprocessed")
        return all.toList()
    }

    private fun hubLine(d: NoteDraft): String? {
        val hub = d.route.hub ?: return null
        val reason = d.route.hubReason ?: "that is where ${d.route.key} notes are collected"
        return "Filed under [[$hub]] because $reason."
    }

    private fun renderNote(d: NoteDraft, stem: String): String {
        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("type: ${Text.yamlString(d.route.type)}\n")
        sb.append("status: ${Text.yamlString(d.route.status)}\n")
        sb.append("date: ${d.date}\n")
        sb.append("tags: ${Text.yamlList(mergedTags(d))}\n")
        sb.append("---\n\n")
        sb.append("# ${d.title.trim()}\n")
        hubLine(d)?.let { sb.append("\n$it\n") }
        if (d.pageCount > 0) sb.append("\n${embeds(d, stem)}\n")
        if (d.userText.isNotBlank()) {
            if (d.pageCount > 0) sb.append("\n## Note\n")
            sb.append("\n${d.userText.trim()}\n")
        }
        if (d.transcription.isNotBlank()) {
            sb.append("\n## Transcription\n")
            sb.append("<!-- Machine transcription. May contain errors; the scan above is the record. -->\n\n")
            sb.append("${d.transcription.trim()}\n")
        }
        return sb.toString()
    }

    private fun renderDocument(d: NoteDraft, stem: String): String {
        val f = d.fields
        val kind = Text.slugify(f["doc_kind"].orEmpty(), 20).ifEmpty { "other" }
        val docDate = f["doc_date"]?.takeIf { Text.isIsoDate(it) }.orEmpty()
        val dueDate = f["due_date"]?.takeIf { Text.isIsoDate(it) }.orEmpty()
        val amount = f["amount"]?.let { Text.parseAmount(it) }.orEmpty()
        val last4 = f["account_last4"].orEmpty().filter { it.isLetterOrDigit() }.takeLast(4)

        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("type: ${Text.yamlString(d.route.type)}\n")
        sb.append("status: ${Text.yamlString(d.route.status)}\n")
        sb.append("doc_kind: $kind\n")
        sb.append("vendor: ${Text.yamlString(f["vendor"].orEmpty())}\n")
        sb.append("amount: $amount\n")
        sb.append("currency: ${Text.yamlString(f["currency"].orEmpty().uppercase())}\n")
        sb.append("doc_date: $docDate\n")
        sb.append("due_date: $dueDate\n")
        sb.append("account_last4: ${if (last4.isEmpty()) "" else Text.yamlQuoted(last4)}\n")
        sb.append("captured: ${d.date}\n")
        sb.append("tags: ${Text.yamlList(mergedTags(d, listOf(kind)))}\n")
        sb.append("---\n\n")
        sb.append("# ${d.title.trim()}\n")
        hubLine(d)?.let { sb.append("\n$it\n") }
        if (d.pageCount > 0) sb.append("\n${embeds(d, stem)}\n")
        if (d.userText.isNotBlank()) sb.append("\n## Note\n\n${d.userText.trim()}\n")
        if (d.summary.isNotBlank()) sb.append("\n## Summary\n\n${d.summary.trim()}\n")
        if (d.transcription.isNotBlank()) {
            sb.append("\n## Transcription\n")
            sb.append("<!-- Machine transcription. May contain errors; the scan above is the record. -->\n\n")
            sb.append("${d.transcription.trim()}\n")
        }
        return sb.toString()
    }

    /** Mirrors 60-Templates/wine-bottle.md in the vault so the note appears in bottles.base. */
    private fun renderWine(d: NoteDraft, stem: String, noteExists: (String) -> Boolean): String {
        val f = d.fields
        fun linkIfExists(name: String): String {
            val slug = Text.slugify(name)
            return if (slug.isNotEmpty() && noteExists(slug)) Text.yamlQuoted("[[$slug]]") else Text.yamlString(name)
        }
        val grapes = f["grapes"].orEmpty().split(',', ';', '/').map { it.trim() }.filter { it.isNotEmpty() }
        val vintage = f["vintage"].orEmpty().filter { it.isDigit() }.take(4)
        val heading = listOf(vintage, f["producer"].orEmpty(), f["wine"].orEmpty())
            .filter { it.isNotBlank() }.joinToString(" ").ifEmpty { d.title.trim() }

        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("type: wine-bottle\n")
        sb.append("status: ${Text.yamlString(d.route.status)}\n")
        sb.append("producer: ${Text.yamlString(f["producer"].orEmpty())}\n")
        sb.append("wine: ${Text.yamlString(f["wine"].orEmpty())}\n")
        sb.append("vintage: $vintage\n")
        sb.append("grapes: ${grapes.joinToString(", ", "[", "]") { linkIfExists(it) }}\n")
        sb.append("place: ${f["place"]?.let { linkIfExists(it) }.orEmpty()}\n")
        sb.append("vintage_note: \n")
        sb.append("country: ${Text.yamlString(f["country"].orEmpty())}\n")
        sb.append("color: ${Text.yamlString(f["color"].orEmpty().lowercase())}\n")
        sb.append("style: []\n")
        sb.append("price: ${f["price"]?.let { Text.parseAmount(it) }.orEmpty()}\n")
        sb.append("drunk_on: ${d.date}\n")
        sb.append("where: ${Text.yamlString(f["where"].orEmpty())}\n")
        sb.append("served: ${Text.yamlString(f["served"].orEmpty())}\n")
        sb.append("again: ${Text.yamlString(f["again"].orEmpty().lowercase())}\n")
        sb.append("tags: ${Text.yamlList(mergedTags(d.copy(tags = emptyList()), listOf("needs-enrichment")))}\n")
        sb.append("---\n\n")
        sb.append("# $heading\n")
        if (d.pageCount > 0) sb.append("\n${embeds(d, stem)}\n")
        sb.append("\n## ${d.route.wordsHeading}\n\n")
        sb.append("**${d.date}**\n")
        if (d.userText.isBlank()) sb.append("> \"\"\n")
        else sb.append(Text.quoteBlock("\"${d.userText.trim()}\"")).append("\n")
        sb.append("\n## What's in the glass\n")
        if (d.transcription.isNotBlank()) {
            sb.append("<!-- Label text, machine-read; to be replaced by research on the desktop. -->\n\n")
            sb.append("${d.transcription.trim()}\n")
        }
        sb.append("\n## Why it tasted that way\n")
        sb.append("\n## Connections\n- \n")
        sb.append("\n## If you liked this, try\n- \n")
        return sb.toString()
    }
}
