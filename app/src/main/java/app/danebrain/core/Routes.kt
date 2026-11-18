package app.danebrain.core

import org.json.JSONObject

/** One destination in the vault. The model picks a key; code owns the folder and filename. */
data class Route(
    val key: String,
    val folder: String,
    val type: String,
    val status: String,
    val tags: List<String>,
    val whenText: String,
    /** "note", "wine-bottle" or "document": which note layout to write. */
    val format: String,
    val datePrefix: Boolean,
    val hub: String?,
    val hubReason: String?,
    /** Heading over the user's own words in a wine note, so it can match an existing template. */
    val wordsHeading: String = DEFAULT_WORDS_HEADING,
) {
    companion object {
        const val DEFAULT_WORDS_HEADING = "In my words"
    }
}

object Routes {
    const val FORMAT_NOTE = "note"
    const val FORMAT_WINE = "wine-bottle"
    const val FORMAT_DOCUMENT = "document"

    /** Parses the route table. Entries without a folder or a "when" are skipped. */
    fun parse(json: String): List<Route> {
        val root = JSONObject(json)
        val out = ArrayList<Route>()
        for (key in root.keys()) {
            val o = root.optJSONObject(key) ?: continue
            val folder = o.optString("folder").trim().trim('/')
            val whenText = o.optString("when").trim()
            if (folder.isEmpty() || whenText.isEmpty() || folder.contains("..")) continue
            val tags = ArrayList<String>()
            o.optJSONArray("tags")?.let { arr -> for (i in 0 until arr.length()) tags.add(arr.getString(i)) }
            out.add(
                Route(
                    key = key,
                    folder = folder,
                    type = o.optString("type", "note"),
                    status = o.optString("status", "inbox"),
                    tags = tags,
                    whenText = whenText,
                    format = o.optString("format", FORMAT_NOTE),
                    datePrefix = o.optBoolean("datePrefix", false),
                    hub = o.optString("hub").ifBlank { null },
                    hubReason = o.optString("hubReason").ifBlank { null },
                    wordsHeading = o.optString("wordsHeading").ifBlank { Route.DEFAULT_WORDS_HEADING },
                )
            )
        }
        require(out.isNotEmpty()) { "Route table is empty" }
        return out
    }

    /** The fallback route: "inbox" when present, otherwise the first entry. */
    fun default(routes: List<Route>): Route =
        routes.firstOrNull { it.key == "inbox" } ?: routes.first()

    fun find(routes: List<Route>, key: String?): Route =
        routes.firstOrNull { it.key == key } ?: default(routes)
}
