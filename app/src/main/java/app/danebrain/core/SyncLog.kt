package app.danebrain.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * A small log of the notes this app has written, kept as JSON text in settings. It answers one
 * question: which of them were written after Obsidian was last open, and so cannot have synced yet.
 */
object SyncLog {
    private const val MAX_ENTRIES = 200

    /** Returns [log] with a note added, oldest entries dropped past [MAX_ENTRIES]. */
    fun add(log: String, notePath: String, writtenAt: Long): String {
        val entries = parse(log) + Entry(notePath, writtenAt)
        val out = JSONArray()
        entries.takeLast(MAX_ENTRIES).forEach { out.put(JSONObject().put("path", it.path).put("at", it.writtenAt)) }
        return out.toString()
    }

    /** Note paths written after [obsidianLastOpen], oldest first. */
    fun waiting(log: String, obsidianLastOpen: Long): List<String> =
        parse(log).filter { it.writtenAt > obsidianLastOpen }.map { it.path }

    /** Returns [log] without the entries Obsidian has already had a chance to sync. */
    fun prune(log: String, obsidianLastOpen: Long): String {
        val out = JSONArray()
        parse(log).filter { it.writtenAt > obsidianLastOpen }
            .forEach { out.put(JSONObject().put("path", it.path).put("at", it.writtenAt)) }
        return out.toString()
    }

    private data class Entry(val path: String, val writtenAt: Long)

    private fun parse(log: String): List<Entry> {
        if (log.isBlank()) return emptyList()
        return try {
            val array = JSONArray(log)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val path = o.optString("path")
                if (path.isEmpty()) null else Entry(path, o.optLong("at"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
