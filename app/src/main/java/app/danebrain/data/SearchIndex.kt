package app.danebrain.data

import android.content.ContentValues
import app.danebrain.core.SearchQuery
import java.io.File

data class SearchHit(val path: String, val title: String, val snippet: String)

/** Full-text index of the vault's markdown files. Read-only with respect to the vault. */
class SearchIndex(private val db: Db) {

    data class RefreshResult(val total: Int, val updated: Int, val removed: Int)

    /** Brings the index in line with the files on disk by comparing size and modified time. */
    @Synchronized
    fun refresh(vaultRoot: File): RefreshResult {
        if (!vaultRoot.isDirectory) return RefreshResult(count(), 0, 0)
        val w = db.writableDatabase
        val known = HashMap<String, Pair<Long, Long>>()
        w.rawQuery("SELECT path, mtime, size FROM notes", null).use { c ->
            while (c.moveToNext()) known[c.getString(0)] = c.getLong(1) to c.getLong(2)
        }
        val seen = HashSet<String>()
        var updated = 0
        w.beginTransaction()
        try {
            for (file in markdownFiles(vaultRoot)) {
                val rel = file.relativeTo(vaultRoot).path.replace(File.separatorChar, '/')
                seen.add(rel)
                val stamp = file.lastModified() to file.length()
                if (known[rel] == stamp) continue
                val text = try {
                    file.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    continue
                }
                val stem = file.nameWithoutExtension
                val title = titleOf(text) ?: stem
                w.delete("fts", "path = ?", arrayOf(rel))
                w.insert("fts", null, ContentValues().apply {
                    put("path", rel)
                    put("title", "$title $stem")
                    put("body", text)
                })
                w.insertWithOnConflict("notes", null, ContentValues().apply {
                    put("path", rel)
                    put("stem", stem.lowercase())
                    put("title", title)
                    put("mtime", stamp.first)
                    put("size", stamp.second)
                }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                updated++
            }
            val gone = known.keys - seen
            for (rel in gone) {
                w.delete("fts", "path = ?", arrayOf(rel))
                w.delete("notes", "path = ?", arrayOf(rel))
            }
            w.setTransactionSuccessful()
            return RefreshResult(seen.size, updated, gone.size)
        } finally {
            w.endTransaction()
        }
    }

    fun count(): Int = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM notes", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    /** True when a note with this filename (no extension, any folder) is in the index. */
    fun stemExists(stem: String): Boolean = pathForStem(stem) != null

    fun pathForStem(stem: String): String? =
        db.readableDatabase.rawQuery("SELECT path FROM notes WHERE stem = ? LIMIT 1", arrayOf(stem.lowercase())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun titleForPath(path: String): String? =
        db.readableDatabase.rawQuery("SELECT title FROM notes WHERE path = ?", arrayOf(path)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /**
     * Notes containing every word of [query]. With [widen], falls back to notes containing any
     * word when nothing contains them all (used by the chatbot, which tends to over-specify).
     */
    fun search(query: String, limit: Int = 50, widen: Boolean = false, prefixLast: Boolean = true): List<SearchHit> {
        val all = SearchQuery.build(query, anyWord = false, prefixLast = prefixLast) ?: return emptyList()
        val hits = run(all, limit)
        if (hits.isNotEmpty() || !widen || SearchQuery.tokens(query).size < 2) return hits
        return run(SearchQuery.build(query, anyWord = true, prefixLast = prefixLast)!!, limit)
    }

    private fun run(match: String, limit: Int): List<SearchHit> {
        val sql = if (db.fts5) {
            "SELECT path, snippet(fts, 2, '$MARK_START', '$MARK_END', '…', 16) FROM fts WHERE fts MATCH ? " +
                "ORDER BY bm25(fts, 0.0, 8.0, 1.0) LIMIT ?"
        } else {
            "SELECT path, snippet(fts, '$MARK_START', '$MARK_END', '…', 2, 16) FROM fts WHERE fts MATCH ? LIMIT ?"
        }
        return try {
            db.readableDatabase.rawQuery(sql, arrayOf(match, limit.toString())).use { c ->
                val out = ArrayList<SearchHit>()
                while (c.moveToNext()) {
                    val path = c.getString(0)
                    out.add(SearchHit(path, titleForPath(path) ?: path, c.getString(1).orEmpty().replace('\n', ' ')))
                }
                out
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun markdownFiles(root: File): Sequence<File> =
        root.walkTopDown()
            .onEnter { it == root || !it.name.startsWith(".") }
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) && !it.name.startsWith(".") }

    private fun titleOf(text: String): String? =
        text.lineSequence().take(80).firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()?.ifEmpty { null }

    companion object {
        const val MARK_START = "⟦"
        const val MARK_END = "⟧"
    }
}
