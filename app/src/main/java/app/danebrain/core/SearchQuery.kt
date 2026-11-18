package app.danebrain.core

object SearchQuery {
    private val token = Regex("[\\p{L}\\p{N}]+")

    fun tokens(input: String): List<String> =
        token.findAll(input.lowercase()).map { it.value }.toList()

    /**
     * Turns what the user typed into a full-text MATCH expression that is valid for both FTS5 and
     * FTS4: lowercase bare words (so AND/OR/NOT/NEAR are never read as operators), with the last
     * word matched as a prefix so results appear while typing. Returns null when there is nothing
     * to search for.
     */
    fun build(input: String, anyWord: Boolean = false, prefixLast: Boolean = true): String? {
        val words = tokens(input)
        if (words.isEmpty()) return null
        val parts = words.mapIndexed { i, w -> if (prefixLast && i == words.lastIndex) "$w*" else w }
        return parts.joinToString(if (anyWord) " OR " else " ")
    }
}
